/**
 * useNavigateToMeetingRoom — jumps from the Issue Panel to the Project
 * Page's meeting room for a given meeting. Issue Panel and Project Page are
 * separate Forge modules (separate iframes), so this is a real page
 * navigation (`@forge/bridge`'s `router`), not an in-app route change — see
 * `utils/meetingRoomHandoff.ts` for how the target meeting survives that.
 *
 * Standalone `pnpm ui:dev` has no Forge router (no real modules to navigate
 * between); `onDevNavigate` lets the caller drive the same DevSurfaceSwitcher
 * surface flip that a real navigation would otherwise cause — see App.tsx.
 */

import { router } from '@forge/bridge';
import { useCallback } from 'react';
import { MODULE_KEY_PROJECT_PAGE } from '../utils/forgeModuleKeys';
import { setPendingMeetingRoom } from '../utils/meetingRoomHandoff';

export function useNavigateToMeetingRoom(onDevNavigate?: () => void) {
    return useCallback(
        (projectKey: string, meetingId: string) => {
            setPendingMeetingRoom(projectKey, meetingId);
            if (import.meta.env.DEV) {
                onDevNavigate?.();
                return;
            }
            void router.navigate({
                target: 'module',
                moduleKey: MODULE_KEY_PROJECT_PAGE,
                projectKey,
            });
        },
        [onDevNavigate],
    );
}
