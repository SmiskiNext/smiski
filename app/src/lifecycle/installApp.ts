/**
 * Records a Forge app installation or major upgrade against the tenant
 * backend.
 *
 * Both `avi:forge:installed:app` and `avi:forge:upgraded:app` map to the same
 * SDK `register()` call (design D3): the backend upsert is idempotent and
 * reactivating, so an upgrade is another `register()` that refreshes the
 * installation id and version.
 *
 * Delivery is via the `trigger` module, which the platform retries up to four
 * times on failure, so a failed call throws rather than being swallowed
 * (design D4).
 */
import {
    register,
    type TenantApp,
    type TenantEnvironment,
    type TenantRegisterTenantRequest,
    type TenantTenantResponse,
} from '@smiskinext/smiski-ts';
import { apiConfig } from '../api/config';
import { forgeApiRemoteClient } from '../api/forgeApiRemoteFetch';
import { describeSdkFailure } from '../api/sdkFailure';
import type { AppInstallationEvent, LifecycleApp } from './events';

/**
 * Map the event's `app` object onto the request's, naming each member rather
 * than spreading it, so a payload member the backend contract has no field for
 * is never forwarded.
 */
function toApp(app: LifecycleApp): TenantApp {
    return {
        id: app.id,
        version: app.version,
        ...(app.name === undefined ? {} : { name: app.name }),
        ...(app.ownerAccountId === undefined
            ? {}
            : { ownerAccountId: app.ownerAccountId }),
    };
}

/**
 * Map the optional `environment` object, omitting it when the event carries
 * none rather than sending an empty object.
 */
function toEnvironment(
    event: AppInstallationEvent,
): { environment: TenantEnvironment } | Record<string, never> {
    return event.environment === undefined
        ? {}
        : { environment: { id: event.environment.id } };
}

/**
 * Resolve the account that performed the life-cycle action. An install reports
 * `installerAccountId` and a major upgrade reports `upgraderAccountId`; the
 * backend contract has a single account field, so the upgrader fills it on an
 * upgrade rather than leaving it unset and clearing the stored installer.
 */
function resolveActorAccountId(
    event: AppInstallationEvent,
): { installerAccountId: string } | Record<string, never> {
    const accountId = event.installerAccountId ?? event.upgraderAccountId;
    return accountId === undefined ? {} : { installerAccountId: accountId };
}

/**
 * Build the register request body. `siteUrl` is absent because no life-cycle
 * payload carries one; the gateway resolves tenant identity from the FIT.
 */
export function toRegisterRequest(
    event: AppInstallationEvent,
): TenantRegisterTenantRequest {
    return {
        id: event.id,
        ...resolveActorAccountId(event),
        app: toApp(event.app),
        ...toEnvironment(event),
    };
}

/**
 * Record the installation, returning the tenant snapshot the backend responded
 * with. Throws on a result whose `error` is present — a non-success status or
 * an unreachable backend — so the Forge platform retries the event delivery.
 */
export async function recordInstallation(
    event: AppInstallationEvent,
): Promise<TenantTenantResponse> {
    const result = await register({
        client: forgeApiRemoteClient,
        path: { version: apiConfig.apiVersion },
        body: toRegisterRequest(event),
    });

    if (result.error !== undefined) {
        throw new Error(
            `Failed to register tenant installation ${event.id}: ${describeSdkFailure(result.error, result.response)}`,
        );
    }

    return result.data;
}
