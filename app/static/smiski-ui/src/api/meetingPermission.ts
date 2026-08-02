/**
 * Meeting permission check — calls Jira's custom `View Meeting`/`Edit Meeting`
 * project permissions (`manifest.yml`'s `jira:projectPermission` module)
 * directly from the browser via `@smiskinext/sdks-jira` bridged to
 * `@forge/bridge`'s `requestJira` (`jiraSdkFetch.ts`), as the invoking user.
 * No resolver hop needed — see `workspaceUsers.ts` for the same pattern.
 */
import { getAllPermissions, getMyPermissions } from '@smiskinext/sdks-jira';
import { jiraCallOptions } from './jiraSdkFetch';

export interface MeetingPermissionResult {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

const VIEW_MEETING_PERMISSION_NAME = 'View Meeting';
const EDIT_MEETING_PERMISSION_NAME = 'Edit Meeting';

/**
 * Resolves the real Jira permission-key strings for the app's declared
 * `view-meeting`/`edit-meeting` custom permissions (declared in
 * `manifest.yml` under `jira:projectPermission`). Jira does not necessarily
 * echo back the bare manifest `key` when listing permissions — matching by
 * the `name` we declared is stable regardless of any internal key prefixing.
 */
async function resolveMeetingPermissionKeys(): Promise<{
    viewKey: string;
    editKey: string;
}> {
    const result = await getAllPermissions({ ...jiraCallOptions });
    if (result.error) {
        throw new Error(
            `Jira permission lookup failed while listing permissions${
                result.response ? ` (status ${result.response.status})` : ''
            }.`,
        );
    }

    const entries = Object.entries(result.data?.permissions ?? {});
    const viewKey = entries.find(
        ([, permission]) => permission.name === VIEW_MEETING_PERMISSION_NAME,
    )?.[0];
    const editKey = entries.find(
        ([, permission]) => permission.name === EDIT_MEETING_PERMISSION_NAME,
    )?.[0];

    if (!viewKey || !editKey) {
        throw new Error(
            'View Meeting / Edit Meeting permissions are not registered on this '
                + 'Jira site yet — deploy the app and confirm the jira:projectPermission '
                + 'module is installed.',
        );
    }

    return { viewKey, editKey };
}

/**
 * Checks the invoking user's `View Meeting`/`Edit Meeting` custom permission
 * for a project, via `GET /rest/api/3/mypermissions` as the invoking user.
 */
export async function getMeetingPermission(
    projectKey: string,
): Promise<MeetingPermissionResult> {
    const { viewKey, editKey } = await resolveMeetingPermissionKeys();

    const result = await getMyPermissions({
        ...jiraCallOptions,
        query: { projectKey, permissions: `${viewKey},${editKey}` },
    });
    if (result.error) {
        throw new Error(
            `Jira permission check failed${
                result.response ? ` (status ${result.response.status})` : ''
            }.`,
        );
    }

    const permissions = result.data?.permissions ?? {};
    return {
        hasViewMeeting: Boolean(permissions[viewKey]?.havePermission),
        hasEditMeeting: Boolean(permissions[editKey]?.havePermission),
    };
}
