/**
 * Records a Forge app uninstall against the tenant backend.
 *
 * Delivery is via the `preUninstall` module: a single non-blocking invocation
 * with a 55-second budget, no retry, and a return value the platform ignores
 * (design D4). A throw would therefore buy nothing, so every outcome is
 * terminal here.
 *
 * Both a `200` (tenant marked uninstalled, or already uninstalled) and a `404`
 * (no tenant row for the resolved cloudId, so nothing to clean up) are
 * successes. Anything else is reported to the app log and swallowed.
 */
import { uninstall } from '@smiskinext/smiski-ts';
import { apiConfig } from '../api/config';
import { forgeApiRemoteClient } from '../api/forgeApiRemoteFetch';
import { describeSdkFailure } from '../api/sdkFailure';

const NOT_FOUND = 404;

/**
 * The outcome of a pre-uninstall invocation, reported to the caller instead of
 * thrown so the handler stays total.
 *
 * - `uninstalled` — the backend marked the tenant uninstalled, or it already
 *   was; the response body carries the tenant snapshot.
 * - `absent` — no tenant row existed for the resolved cloudId.
 * - `failed` — anything else, carrying a readable reason for the app log.
 */
export type UninstallOutcome =
    | { status: 'uninstalled' }
    | { status: 'absent' }
    | { status: 'failed'; reason: string };

/**
 * Record the uninstall. Never throws: the platform discards both a return
 * value and an error from a pre-uninstall invocation, so a failure is logged
 * and the uninstallation is allowed to proceed.
 */
export async function recordUninstall(): Promise<UninstallOutcome> {
    const result = await uninstall({
        client: forgeApiRemoteClient,
        path: { version: apiConfig.apiVersion },
    });

    if (result.error === undefined) {
        return { status: 'uninstalled' };
    }

    if (result.response?.status === NOT_FOUND) {
        return { status: 'absent' };
    }

    return {
        status: 'failed',
        reason: describeSdkFailure(result.error, result.response),
    };
}
