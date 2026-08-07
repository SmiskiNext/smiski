import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
    execInService,
    isServiceRunning,
    PINNED_IMAGES,
    runOnStackNetwork,
    runPsql,
} from './docker.ts';
import { HARNESS_TENANT_ID } from './fixtures.ts';

const VALKEY_SERVICE = 'valkey';
const MEET_POSTGRES_SERVICE = 'meet-postgres';

/**
 * Container name the load generator runs under.
 *
 * cAdvisor labels its per-container series with this name, so resource queries
 * can attribute the generator's own consumption and subtract it from the system
 * under test. An unnamed run receives a random name and the series becomes
 * unfindable, which reads as absent load rather than as a broken query.
 * `collect.ts` matches on this exact value.
 */
export const LOAD_GENERATOR_CONTAINER_NAME = 'smiski-test-k6';

/**
 * Key prefix the gateway caches permission lookups under.
 *
 * Mirrors `Cache.GenerateKey` in `services/gateway/internal/cache/valkey.go`,
 * which formats `perm:{cloudId}:{accountId}:{context}`. A companion `:stale` key
 * with a much longer retention backs the gateway's fallback path, so clearing
 * only the primary key leaves a warm path behind and the "cold" pass is not cold.
 */
const PERMISSION_KEY_PATTERN = 'perm:*';

export type LoadVariant = 'single' | 'sharded';
export type CacheState = 'cold' | 'warm';
export type AdmissionPolicy = 'ALLOW_ALL' | 'MANUAL_APPROVAL';

export interface TokenLoadRequest {
    variant: LoadVariant;
    cacheState: CacheState;
    admissionPolicy: AdmissionPolicy;
    networkProfile: string;
    gatewayOrigin: string;
    fitToken: string;
    issueId: string;
    systemToken: string;
    meetingIds: string[];
    requestCount: number;
    windowSeconds: number;
}

export interface TokenLoadSummary {
    variant: LoadVariant;
    cacheState: CacheState;
    admissionPolicy: AdmissionPolicy;
    requestCount: number;
    dispatchedCount: number;
    meetingCount: number;
    approved: number;
    pending: number;
    tokensIssued: number;
    failures: Record<string, number>;
    failureTotal: number;
    droppedIterations: number;
    latencyMedianMs: number | null;
    latencyP95Ms: number | null;
    latencyP99Ms: number | null;
    latencyMaxMs: number | null;
    successRate: number;
}

/**
 * Removes the gateway's cached permission entries.
 *
 * Both the primary and the `:stale` key must go. The gateway falls back to the
 * stale copy whenever the Jira call fails, and its retention is 24 hours by
 * default, so leaving it in place makes a nominally cold pass indistinguishable
 * from a warm one — with no error to reveal it.
 */
export async function clearGatewayPermissionCache(): Promise<number> {
    const listed = await execInService(VALKEY_SERVICE, [
        'valkey-cli',
        '--scan',
        '--pattern',
        PERMISSION_KEY_PATTERN,
    ]);

    if (listed.exitCode !== 0) {
        throw new Error(
            `Could not enumerate cache keys: ${listed.stderr.trim() || 'valkey-cli failed'}`,
        );
    }

    const keys = listed.stdout
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '');

    if (keys.length === 0) {
        return 0;
    }

    const deleted = await execInService(VALKEY_SERVICE, [
        'valkey-cli',
        'DEL',
        ...keys,
    ]);

    if (deleted.exitCode !== 0) {
        throw new Error(
            `Could not delete cache keys: ${deleted.stderr.trim() || 'valkey-cli failed'}`,
        );
    }

    return keys.length;
}

/** Reads the meeting identifiers seeded under a given admission policy. */
export async function readSeededMeetingIds(
    admissionPolicy: AdmissionPolicy,
    tenantId: string = HARNESS_TENANT_ID,
): Promise<string[]> {
    const result = await runPsql(
        MEET_POSTGRES_SERVICE,
        'meet',
        'smiski_user',
        `SELECT id FROM meetings
         WHERE tenant_id = '${tenantId}'
           AND project_key = 'LOAD'
           AND status = 'RUNNING'
           AND deleted_at IS NULL
           AND settings->>'admissionPolicy' = '${admissionPolicy}'
         ORDER BY id;`,
    );

    if (result.exitCode !== 0) {
        throw new Error(
            `Could not read seeded meetings: ${result.stderr.trim() || 'psql failed'}`,
        );
    }

    return result.stdout
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '');
}

function metricCount(metrics: Record<string, unknown>, name: string): number {
    const metric = metrics[name] as
        | { values?: { count?: unknown } }
        | undefined;
    const count = metric?.values?.count;
    return typeof count === 'number' ? count : 0;
}

function metricTrend(
    metrics: Record<string, unknown>,
    name: string,
    statistic: string,
): number | null {
    const metric = metrics[name] as
        | { values?: Record<string, unknown> }
        | undefined;
    const value = metric?.values?.[statistic];
    return typeof value === 'number' ? value : null;
}

/**
 * Runs one pass of the token-issuance load and parses its summary.
 *
 * The k6 image writes its summary into a bind-mounted results directory rather
 * than being parsed from stdout: stdout also carries progress output, and
 * scraping it would break on any k6 formatting change.
 */
export async function runTokenLoad(
    request: TokenLoadRequest,
    scriptDirectory: string,
    resultsDirectory: string,
): Promise<TokenLoadSummary> {
    if (!(await isServiceRunning('envoy'))) {
        throw new Error(
            'The test stack is not running. Start it before measuring, or every '
                + 'request fails as a transport error and the run measures nothing.',
        );
    }

    await mkdir(resultsDirectory, { recursive: true });

    const result = await runOnStackNetwork(
        PINNED_IMAGES.k6,
        ['run', '/scripts/token-load.js'],
        {
            asCurrentUser: true,
            containerName: LOAD_GENERATOR_CONTAINER_NAME,
            mounts: [
                `${scriptDirectory}:/scripts:ro`,
                `${resultsDirectory}:/results`,
            ],
            environment: {
                GATEWAY_ORIGIN: request.gatewayOrigin,
                FIT_TOKEN: request.fitToken,
                ISSUE_ID: request.issueId,
                SYSTEM_TOKEN: request.systemToken,
                MEETING_IDS: request.meetingIds.join(','),
                REQUEST_COUNT: String(request.requestCount),
                WINDOW_SECONDS: String(request.windowSeconds),
                VARIANT: request.variant,
                CACHE_STATE: request.cacheState,
                ADMISSION_POLICY: request.admissionPolicy,
                NETWORK_PROFILE: request.networkProfile,
            },
        },
    );

    const summaryPath = resolve(resultsDirectory, 'summary.json');
    const summaryContent = await readFile(summaryPath, 'utf8').catch(
        () => null,
    );

    if (summaryContent === null) {
        throw new Error(
            'k6 produced no summary. Its output was:\n'
                + `${result.stderr.trim() || result.stdout.trim()}`,
        );
    }

    const parsed = JSON.parse(summaryContent) as {
        metrics: Record<string, unknown>;
    };

    return summariseMetrics(request, parsed.metrics);
}

/**
 * Reduces a k6 metric set to the figures the test plan asks for.
 *
 * Success rate is expressed against what the generator actually offered, not
 * against the requested count. A constant-arrival-rate scenario overshoots its
 * nominal figure slightly at second boundaries, so dividing by the request
 * count yields rates above 100% and conceals whether anything failed. Requests
 * k6 dropped before dispatch are included in the denominator for the same
 * reason: a dropped iteration is load the system never saw, and omitting it
 * would flatter the result.
 */
function summariseMetrics(
    request: TokenLoadRequest,
    metrics: Record<string, unknown>,
): TokenLoadSummary {
    const approved = metricCount(metrics, 'outcome_approved');
    const pending = metricCount(metrics, 'outcome_pending');

    const failures: Record<string, number> = {};
    for (const [label, metricName] of [
        ['http 401 unauthenticated', 'outcome_http_401'],
        ['http 403 forbidden', 'outcome_http_403'],
        ['http 404 not found', 'outcome_http_404'],
        ['http 409 conflict', 'outcome_http_409'],
        ['http 5xx server error', 'outcome_http_5xx'],
        ['unexpected response', 'outcome_unexpected'],
        ['transport error', 'outcome_transport_error'],
    ] as Array<[string, string]>) {
        const count = metricCount(metrics, metricName);
        if (count > 0) {
            failures[label] = count;
        }
    }

    const failureTotal = Object.values(failures).reduce(
        (total, count) => total + count,
        0,
    );

    const dispatchedCount = approved + pending + failureTotal;
    const offeredCount =
        dispatchedCount + metricCount(metrics, 'dropped_iterations');

    const successRate =
        offeredCount === 0 ? 0 : ((approved + pending) / offeredCount) * 100;

    return {
        variant: request.variant,
        cacheState: request.cacheState,
        admissionPolicy: request.admissionPolicy,
        requestCount: request.requestCount,
        dispatchedCount,
        meetingCount: request.meetingIds.length,
        approved,
        pending,
        tokensIssued: metricCount(metrics, 'token_issued'),
        failures,
        failureTotal,
        droppedIterations: metricCount(metrics, 'dropped_iterations'),
        latencyMedianMs: metricTrend(metrics, 'join_latency', 'med'),
        latencyP95Ms: metricTrend(metrics, 'join_latency', 'p(95)'),
        latencyP99Ms: metricTrend(metrics, 'join_latency', 'p(99)'),
        latencyMaxMs: metricTrend(metrics, 'join_latency', 'max'),
        successRate,
    };
}

/** Writes one pass's summary under a name naming its full condition set. */
export async function writePassSummary(
    summary: TokenLoadSummary,
    networkProfile: string,
    resultsDirectory: string,
): Promise<string> {
    const fileName =
        `tc04-${summary.admissionPolicy.toLowerCase()}-${summary.variant}`
        + `-${summary.cacheState}-${networkProfile}.json`;
    const path = resolve(resultsDirectory, fileName);

    await writeFile(
        path,
        `${JSON.stringify({ ...summary, networkProfile }, null, 4)}\n`,
        'utf8',
    );

    return path;
}
