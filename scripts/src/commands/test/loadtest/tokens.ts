import { defineCommand } from 'citty';
import {
    buildFitClaims,
    DEFAULT_APP_ARI,
    DEFAULT_ENVIRONMENT_ARI,
    FIT_AUDIENCE,
    loadSigningKey,
    signFit,
} from '../../../lib/fit.ts';
import {
    DEFAULT_GATEWAY_ORIGIN,
    HARNESS_ACCOUNT_ID,
    HARNESS_ISSUE_ID,
    HARNESS_SYSTEM_TOKEN,
    HARNESS_TENANT_ID,
} from '../../../lib/fixtures.ts';
import {
    type AdmissionPolicy,
    type CacheState,
    clearGatewayPermissionCache,
    type LoadVariant,
    readSeededMeetingIds,
    runTokenLoad,
    type TokenLoadSummary,
    writePassSummary,
} from '../../../lib/loadtest.ts';
import {
    k6ScriptDirectory,
    testKeyDirectory,
    testResultsDirectory,
} from '../../../lib/paths.ts';

const TOKEN_LIFETIME_SECONDS = 3600;

/**
 * Threshold TC-04 states, asserted against exactly one pass.
 *
 * Applied only to the warm-cache sharded pass, which represents steady state.
 * The single-room passes measure the pessimistic row lock's queueing, and the
 * cold passes include a permission lookup that a real steady-state request does
 * not make; asserting against either would be asserting against a condition the
 * threshold was never written for.
 */
const RESPONSE_TIME_THRESHOLD_MS = 500;

export const tokensCommand = defineCommand({
    meta: {
        name: 'tokens',
        description: 'TC-04: concurrent access-token requests, all four passes',
    },
    args: {
        'gateway-origin': {
            type: 'string',
            description:
                'Origin of the Envoy ingress; defaults to the in-stack address '
                + 'the containerised generator reaches, not the host port',
            default: DEFAULT_GATEWAY_ORIGIN,
        },
        'request-count': {
            type: 'string',
            description: 'Requests dispatched per pass',
            default: '500',
        },
        'window-seconds': {
            type: 'string',
            description: 'Window the requests are dispatched within',
            default: '1',
        },
        'admission-policy': {
            type: 'string',
            description:
                'ALLOW_ALL for token throughput, MANUAL_APPROVAL for cached state',
            default: 'ALLOW_ALL',
        },
        variants: {
            type: 'string',
            description: 'Comma-separated subset of single,sharded',
            default: 'single,sharded',
        },
        'cache-states': {
            type: 'string',
            description: 'Comma-separated subset of cold,warm',
            default: 'cold,warm',
        },
        'network-profile': {
            type: 'string',
            description:
                'Impairment profile in force, recorded into each result',
            default: 'lan',
        },
        'tenant-id': {
            type: 'string',
            description: 'Seeded tenant to measure against',
            default: HARNESS_TENANT_ID,
        },
        'account-id': {
            type: 'string',
            description:
                'Caller identity; must not be the host under MANUAL_APPROVAL',
            default: HARNESS_ACCOUNT_ID,
        },
        'key-dir': {
            type: 'string',
            description: 'Directory holding the signing key material',
            default: testKeyDirectory,
        },
        'results-dir': {
            type: 'string',
            description: 'Directory results are written to',
            default: testResultsDirectory,
        },
    },
    async run({ args }) {
        const admissionPolicy = args[
            'admission-policy'
        ].toUpperCase() as AdmissionPolicy;

        if (
            admissionPolicy !== 'ALLOW_ALL'
            && admissionPolicy !== 'MANUAL_APPROVAL'
        ) {
            console.error(
                '--admission-policy must be ALLOW_ALL or MANUAL_APPROVAL',
            );
            process.exitCode = 1;
            return;
        }

        const requestCount = Number.parseInt(args['request-count'], 10);
        const windowSeconds = Number.parseInt(args['window-seconds'], 10);

        if (
            !Number.isInteger(requestCount)
            || requestCount <= 0
            || !Number.isInteger(windowSeconds)
            || windowSeconds <= 0
        ) {
            console.error(
                '--request-count and --window-seconds must both be positive integers',
            );
            process.exitCode = 1;
            return;
        }

        const variants = parseVariants(args.variants);
        const cacheStates = parseCacheStates(args['cache-states']);

        if (variants === null || cacheStates === null) {
            console.error(
                '--variants accepts single,sharded and --cache-states accepts cold,warm',
            );
            process.exitCode = 1;
            return;
        }

        const key = await loadSigningKey(args['key-dir']).catch(
            (error: unknown) => error as Error,
        );

        if (key instanceof Error) {
            console.error(key.message);
            process.exitCode = 1;
            return;
        }

        const meetingIds = await readSeededMeetingIds(
            admissionPolicy,
            args['tenant-id'],
        ).catch((error: unknown) => error as Error);

        if (meetingIds instanceof Error) {
            console.error(meetingIds.message);
            process.exitCode = 1;
            return;
        }

        if (meetingIds.length === 0) {
            console.error(
                `No ${admissionPolicy} meetings are seeded for tenant `
                    + `${args['tenant-id']}. Run \`smiski test seed\` first.`,
            );
            process.exitCode = 1;
            return;
        }

        const fitToken = signFit(
            buildFitClaims({
                cloudId: args['tenant-id'],
                accountId: args['account-id'],
                appAri: DEFAULT_APP_ARI,
                environmentAri: DEFAULT_ENVIRONMENT_ARI,
                audience: FIT_AUDIENCE,
                expiresInSeconds: TOKEN_LIFETIME_SECONDS,
            }),
            key,
        );

        const summaries: TokenLoadSummary[] = [];

        for (const variant of variants) {
            const targetMeetings =
                variant === 'single' ? meetingIds.slice(0, 1) : meetingIds;

            for (const cacheState of cacheStates) {
                if (cacheState === 'cold') {
                    const cleared = await clearGatewayPermissionCache().catch(
                        (error: unknown) => error as Error,
                    );

                    if (cleared instanceof Error) {
                        console.error(cleared.message);
                        process.exitCode = 1;
                        return;
                    }

                    console.log(
                        `\n[${variant}/${cacheState}] cleared ${cleared} cache keys`,
                    );
                } else {
                    console.log(
                        `\n[${variant}/${cacheState}] cache left populated`,
                    );
                }

                const summary = await runTokenLoad(
                    {
                        variant,
                        cacheState,
                        admissionPolicy,
                        networkProfile: args['network-profile'],
                        gatewayOrigin: args['gateway-origin'],
                        fitToken,
                        issueId: HARNESS_ISSUE_ID,
                        systemToken: HARNESS_SYSTEM_TOKEN,
                        meetingIds: targetMeetings,
                        requestCount,
                        windowSeconds,
                    },
                    k6ScriptDirectory,
                    args['results-dir'],
                ).catch((error: unknown) => error as Error);

                if (summary instanceof Error) {
                    console.error(summary.message);
                    process.exitCode = 1;
                    return;
                }

                const path = await writePassSummary(
                    summary,
                    args['network-profile'],
                    args['results-dir'],
                );

                reportPass(summary, path);
                summaries.push(summary);
            }
        }

        reportComparison(summaries);
    },
});

function parseVariants(raw: string): LoadVariant[] | null {
    const parts = raw.split(',').map((part) => part.trim());
    const isValid = parts.every(
        (part) => part === 'single' || part === 'sharded',
    );
    return isValid && parts.length > 0 ? (parts as LoadVariant[]) : null;
}

function parseCacheStates(raw: string): CacheState[] | null {
    const parts = raw.split(',').map((part) => part.trim());
    const isValid = parts.every((part) => part === 'cold' || part === 'warm');
    return isValid && parts.length > 0 ? (parts as CacheState[]) : null;
}

function formatMilliseconds(value: number | null): string {
    return value === null ? 'n/a' : `${value.toFixed(1)} ms`;
}

function reportPass(summary: TokenLoadSummary, path: string): void {
    console.log(
        `  variant           ${summary.variant} (${summary.meetingCount} meeting(s))`,
    );
    console.log(`  admission policy  ${summary.admissionPolicy}`);
    console.log(
        `  offered load      ${summary.requestCount} requested,`
            + ` ${summary.dispatchedCount} dispatched`,
    );
    console.log(
        `  approved          ${summary.approved}   pending ${summary.pending}`
            + `   tokens issued ${summary.tokensIssued}`,
    );
    console.log(`  success rate      ${summary.successRate.toFixed(2)}%`);
    console.log(
        `  median / p95      ${formatMilliseconds(summary.latencyMedianMs)}`
            + ` / ${formatMilliseconds(summary.latencyP95Ms)}`,
    );
    console.log(
        `  p99 / max         ${formatMilliseconds(summary.latencyP99Ms)}`
            + ` / ${formatMilliseconds(summary.latencyMaxMs)}`,
    );

    if (summary.droppedIterations > 0) {
        console.log(
            `  dropped           ${summary.droppedIterations} iterations were never `
                + 'dispatched — the offered load was not achieved',
        );
    }

    if (summary.failureTotal === 0) {
        console.log('  failures          none');
    } else {
        console.log(`  failures          ${summary.failureTotal} total`);
        for (const [reason, count] of Object.entries(summary.failures)) {
            console.log(`                    ${count} x ${reason}`);
        }
    }

    console.log(`  written           ${path}`);
}

/**
 * Prints the two variants side by side and asserts the threshold once.
 *
 * The single-room and sharded figures are never averaged: a single meeting
 * serialises every join behind one `PESSIMISTIC_WRITE` lock, so its tail latency
 * measures the lock queue while the sharded figure measures throughput. An
 * average of the two describes no real condition.
 */
function reportComparison(summaries: TokenLoadSummary[]): void {
    console.log('\n── TC-04 summary ─────────────────────────────────────────');

    for (const summary of summaries) {
        console.log(
            `${summary.variant.padEnd(8)} ${summary.cacheState.padEnd(5)}`
                + ` median ${formatMilliseconds(summary.latencyMedianMs).padStart(10)}`
                + `  p95 ${formatMilliseconds(summary.latencyP95Ms).padStart(10)}`
                + `  p99 ${formatMilliseconds(summary.latencyP99Ms).padStart(10)}`
                + `  success ${summary.successRate.toFixed(1)}%`,
        );
    }

    const authoritative = summaries.find(
        (summary) =>
            summary.variant === 'sharded' && summary.cacheState === 'warm',
    );

    if (authoritative === undefined) {
        console.log(
            '\nNo warm-cache sharded pass ran, so the '
                + `${RESPONSE_TIME_THRESHOLD_MS} ms threshold was not asserted. `
                + 'Every figure above is context only.',
        );
        return;
    }

    const median = authoritative.latencyMedianMs;
    const verdict =
        median === null
            ? 'not measurable'
            : median < RESPONSE_TIME_THRESHOLD_MS
              ? 'PASS'
              : 'FAIL';

    console.log(
        `\nThreshold (< ${RESPONSE_TIME_THRESHOLD_MS} ms) applies to the warm-cache `
            + `sharded pass only: median ${formatMilliseconds(median)} -> ${verdict}`,
    );
    console.log(
        'Single-room and cold-cache figures are supporting context. The '
            + 'single-room tail measures the pessimistic row lock, not throughput.',
    );
}
