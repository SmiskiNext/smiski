/**
 * Shapes of the Forge app life-cycle event payloads this app subscribes to,
 * per https://developer.atlassian.com/platform/forge/events-reference/life-cycle/.
 *
 * Only the members the tenant registration contract consumes are modelled. The
 * upgrade payload's `permissions` is deliberately absent: the backend has no
 * field for it and it does not affect tenant identity (design D3).
 */

/** The `app` object both the install and the upgrade payload carry. */
export interface LifecycleApp {
    id: string;
    version: string;
    name?: string;
    ownerAccountId?: string;
}

/** The optional `environment` object both payloads carry. */
export interface LifecycleEnvironment {
    id: string;
}

/**
 * `avi:forge:installed:app` and `avi:forge:upgraded:app`.
 *
 * The two differ only in which account they name: an install reports
 * `installerAccountId`, a major upgrade reports `upgraderAccountId`. Both are
 * optional, so either may be absent.
 */
export interface AppInstallationEvent {
    id: string;
    installerAccountId?: string;
    upgraderAccountId?: string;
    app: LifecycleApp;
    environment?: LifecycleEnvironment;
}
