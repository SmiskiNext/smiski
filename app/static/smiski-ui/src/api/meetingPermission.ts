/**
 * Meeting permission check — routed through the Forge resolver, which queries
 * Jira's custom `View Meeting`/`Edit Meeting` project permissions
 * (`manifest.yml`'s `jira:projectPermission` module) as the invoking user.
 */
import { invoke } from '@forge/bridge';

export interface MeetingPermissionResult {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

export async function getMeetingPermission(
    projectKey: string,
): Promise<MeetingPermissionResult> {
    return invoke('getMeetingPermission', {
        projectKey,
    }) as Promise<MeetingPermissionResult>;
}
