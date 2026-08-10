/**
 * Fixture identities shared by every harness command.
 *
 * These are constants rather than arguments because three independent
 * components must agree on them or measurement fails in ways that do not look
 * like a configuration error:
 *
 * - `token` signs `cloudId` into the Forge Invocation Token
 * - `seed` writes the same value as `meetings.tenant_id`
 * - the gateway derives its Valkey cache key from the token's `cloudId`
 *
 * A disagreement between the first two yields 404 from Hibernate's `@TenantId`
 * filter after a fully successful authentication, which reads as a missing
 * fixture rather than as a mismatch.
 */

/** Tenant the harness seeds and signs tokens for. */
export const HARNESS_TENANT_ID = 'smiski-loadtest-tenant';

/** Account the harness authenticates as by default. */
export const HARNESS_ACCOUNT_ID = 'loadtest-participant';

/**
 * Account identifier of the seeded meetings' host.
 *
 * Under `MANUAL_APPROVAL` the host is admitted immediately while everyone else
 * is queued, so TC-04b needs a caller that is deliberately NOT this value.
 */
export const HARNESS_HOST_ACCOUNT_ID = 'loadtest-host';

/**
 * Jira issue identifier sent as `x-issue-id`.
 *
 * Must be numeric: the gateway parses it with `strconv.ParseInt` and returns a
 * 403 naming a non-numeric value. Omitting the header entirely is worse — the
 * gateway skips the permission check and returns an empty permission set, which
 * then fails `@PreAuthorize("hasAuthority('view-meeting'))` as a 403 that looks
 * like a permission problem rather than a missing header.
 */
export const HARNESS_ISSUE_ID = '10001';

/**
 * Value sent as `x-forge-oauth-system`.
 *
 * The gateway treats an absent system token as a configuration fault and
 * returns 500, so this must be present and non-empty. Its content is never
 * validated: the mock Jira ignores credentials entirely.
 */
export const HARNESS_SYSTEM_TOKEN = 'loadtest-system-token';

/** Default gateway origin, matching the inherited Envoy host port. */
export const DEFAULT_GATEWAY_ORIGIN = 'http://localhost:30000';

/** Default browser-facing LiveKit signalling URL for a host browser. */
export const DEFAULT_LIVEKIT_WS_URL = 'ws://localhost:7880';

/**
 * LiveKit signalling URL for the containerised NAT browser (TC-01).
 *
 * That browser sits on the client network and cannot resolve the
 * `livekit-server` service name, so it addresses the media server by the static
 * address the overlay pins it to, routed through nat-gw. The host port is not
 * reachable from inside the client network, so `localhost` would not work there.
 */
export const NAT_CLIENT_LIVEKIT_WS_URL = 'ws://10.77.0.10:7880';

/** Default in-stack LiveKit signalling URL for server-side tooling. */
export const DEFAULT_LIVEKIT_URL = 'http://livekit-server:7880';
