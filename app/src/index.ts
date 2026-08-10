/**
 * Forge function entry point. `manifest.yml`'s `function` modules point their
 * `handler` at `index.<name>` for each export below — Forge resolves the
 * handler as `<file>.<export>` relative to this `src/` root.
 */
import type { AppInstallationEvent } from './lifecycle/events';
import { recordInstallation } from './lifecycle/installApp';
import { recordUninstall } from './lifecycle/uninstallApp';

/**
 * `trigger` handler for `avi:forge:installed:app` and
 * `avi:forge:upgraded:app`. Throws on failure so the Forge platform retries
 * the event delivery (up to 4 attempts).
 */
export async function installOrUpgrade(
    event: AppInstallationEvent,
): Promise<void> {
    await recordInstallation(event);
}

/**
 * `preUninstall` handler. Never throws — the platform ignores both a return
 * value and an error from this invocation — so a failure is logged instead.
 */
export async function preUninstall(): Promise<void> {
    const outcome = await recordUninstall();
    if (outcome.status === 'failed') {
        console.error(`Pre-uninstall could not be recorded: ${outcome.reason}`);
    }
}
