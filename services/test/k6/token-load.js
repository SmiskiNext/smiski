// k6 scenario for TC-04: concurrent access-token issuance through the gateway.
//
// Runs against envoy, not directly against `meet`, so the measured latency
// includes RS256 verification, the Lua claim filter, the ext_authz gRPC hop and
// the gateway's Valkey lookup — every hop a real request crosses.
//
// The scenario is an OPEN model (`constant-arrival-rate`): 500 requests are
// dispatched within the configured window regardless of how fast the system
// answers. A closed model would silently reduce the offered load when the
// pessimistic row lock started queueing, reporting a comfortable latency for a
// load that was never actually applied.
//
// Configured entirely through environment variables so one script serves all
// four passes (single/sharded x cold/warm) and the variant is recorded in the
// summary rather than inferred from which file was run.
//
// Every outcome is counted under a named counter. A request that fails is a
// reported observation, never an omission: k6 excludes failed requests from some
// built-in aggregates, so relying on `http_req_duration` alone would let a run
// that rejected most of its requests look fast.

import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const gatewayOrigin = __ENV.GATEWAY_ORIGIN;
const fitToken = __ENV.FIT_TOKEN;
const issueId = __ENV.ISSUE_ID;
const systemToken = __ENV.SYSTEM_TOKEN;
const meetingIds = (__ENV.MEETING_IDS || '').split(',').filter(Boolean);

const requestCount = Number.parseInt(__ENV.REQUEST_COUNT || '500', 10);
const windowSeconds = Number.parseInt(__ENV.WINDOW_SECONDS || '1', 10);

const variant = __ENV.VARIANT || 'unknown';
const cacheState = __ENV.CACHE_STATE || 'unknown';
const admissionPolicy = __ENV.ADMISSION_POLICY || 'unknown';
const networkProfile = __ENV.NETWORK_PROFILE || 'unknown';

const outcomeApproved = new Counter('outcome_approved');
const outcomePending = new Counter('outcome_pending');
const outcomeUnauthenticated = new Counter('outcome_http_401');
const outcomeForbidden = new Counter('outcome_http_403');
const outcomeNotFound = new Counter('outcome_http_404');
const outcomeConflict = new Counter('outcome_http_409');
const outcomeServerError = new Counter('outcome_http_5xx');
const outcomeUnexpected = new Counter('outcome_unexpected');
const outcomeTransportError = new Counter('outcome_transport_error');

const tokenIssued = new Counter('token_issued');
const joinLatency = new Trend('join_latency', true);

/**
 * Scenario and reporting configuration.
 *
 * Virtual users are pre-allocated generously so the arrival rate is actually
 * achieved. When it is not, k6 reports `dropped_iterations`, which the CLI
 * folds into the offered-load denominator rather than hiding.
 *
 * `thresholds` is deliberately empty. Which pass is authoritative is a decision
 * the CLI makes — only the warm-cache sharded variant is asserted against the
 * 500 ms budget. A threshold here would fail the cold and single-room passes,
 * which exist precisely to be slower.
 */
export const options = {
    scenarios: {
        token_issuance: {
            executor: 'constant-arrival-rate',
            rate: Math.ceil(requestCount / windowSeconds),
            timeUnit: '1s',
            duration: `${windowSeconds}s`,
            preAllocatedVUs: requestCount,
            maxVUs: requestCount * 2,
            gracefulStop: '60s',
        },
    },
    thresholds: {},
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    if (meetingIds.length === 0) {
        throw new Error('MEETING_IDS is empty; run `smiski test seed` first');
    }

    if (!fitToken) {
        throw new Error('FIT_TOKEN is empty; run `smiski test token` first');
    }

    return { meetingIds };
}

/**
 * Selects the target meeting for this iteration.
 *
 * Round-robin rather than random so the sharded variant spreads load evenly and
 * deterministically. An uneven split would leave one meeting under more lock
 * contention than the others and blur the very comparison the two variants
 * exist to make.
 */
function selectMeetingId(iterationIndex, ids) {
    return ids[iterationIndex % ids.length];
}

/**
 * Issues one join request and classifies its outcome.
 *
 * The device identifier is unique per iteration rather than reused. Under
 * `MANUAL_APPROVAL` a repeated join from the same device is idempotent and
 * returns the existing request, so a shared identifier would collapse 500
 * requests into one write and understate the path TC-04b measures.
 */
export default function tokenIssuanceIteration(data) {
    const iterationIndex = __VU * 100000 + __ITER;
    const meetingId = selectMeetingId(iterationIndex, data.meetingIds);
    const uniqueDeviceId = `k6-device-${__VU}-${__ITER}`;

    const response = http.post(
        `${gatewayOrigin}/api/1/meetings/${meetingId}:join`,
        JSON.stringify({
            displayName: `k6-${__VU}-${__ITER}`,
            deviceId: uniqueDeviceId,
        }),
        {
            headers: {
                'Authorization': `Bearer ${fitToken}`,
                'Content-Type': 'application/json',
                'x-issue-id': issueId,
                'x-forge-oauth-system': systemToken,
            },
            tags: { variant, cache_state: cacheState },
        },
    );

    joinLatency.add(response.timings.duration);
    recordOutcome(response);
}

function recordOutcome(response) {
    if (response.error_code !== 0 && response.status === 0) {
        outcomeTransportError.add(1);
        return;
    }

    switch (response.status) {
        case 200:
            recordSuccessBody(response);
            return;
        case 401:
            outcomeUnauthenticated.add(1);
            return;
        case 403:
            outcomeForbidden.add(1);
            return;
        case 404:
            outcomeNotFound.add(1);
            return;
        case 409:
            outcomeConflict.add(1);
            return;
        default:
            if (response.status >= 500) {
                outcomeServerError.add(1);
                return;
            }
            outcomeUnexpected.add(1);
    }
}

/**
 * Separates an admitted join from a queued one.
 *
 * Both answer 200, and the difference is the whole reason TC-04 splits in two:
 * under `ALLOW_ALL` the response carries a token and touches no Redis, while
 * under `MANUAL_APPROVAL` Redis is written and `token` is null. Counting only
 * HTTP status would report a run of queued requests as 100% token success.
 */
function recordSuccessBody(response) {
    let body;
    try {
        body = response.json();
    } catch (error) {
        outcomeUnexpected.add(1);
        return;
    }

    if (body && body.status === 'APPROVED') {
        outcomeApproved.add(1);
        if (body.token) {
            tokenIssued.add(1);
        }
        return;
    }

    if (body && body.status === 'PENDING') {
        outcomePending.add(1);
        return;
    }

    outcomeUnexpected.add(1);
}

export function handleSummary(data) {
    return {
        '/results/summary.json': JSON.stringify(
            {
                run: {
                    variant,
                    cacheState,
                    admissionPolicy,
                    networkProfile,
                    requestCount,
                    windowSeconds,
                    meetingCount: meetingIds.length,
                },
                metrics: data.metrics,
            },
            null,
            2,
        ),
    };
}
