/**
 * useNavigateToMeetingRoom — jumps from the Issue Panel to the Project
 * Page's meeting room for a given meeting. Issue Panel and Project Page are
 * separate Forge modules (separate iframes), so this is a real page
 * navigation (`@forge/bridge`'s `router`), not an in-app route change — see
 * `utils/meetingRoomHandoff.ts` for how the target meeting survives that.
 */

import { router } from '@forge/bridge';
import { useCallback } from 'react';
import { MODULE_KEY_PROJECT_PAGE } from '../utils/forgeModuleKeys';
import { setPendingMeetingRoom } from '../utils/meetingRoomHandoff';

export function useNavigateToMeetingRoom() {
    return useCallback((projectKey: string, meetingId: string) => {
        setPendingMeetingRoom(projectKey, meetingId);
        void router.navigate({
            target: 'module',
            moduleKey: MODULE_KEY_PROJECT_PAGE,
            projectKey,
        });
    }, []);
}
