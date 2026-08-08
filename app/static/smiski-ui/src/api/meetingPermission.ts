/**
 * Meeting permission checks — the app's custom `View Meeting`/`Edit Meeting`
 * project permissions (`manifest.yml`'s `jira:projectPermission` module), read
 * directly from the browser via `@smiskinext/sdks-jira` bridged to
 * `@forge/bridge`'s `requestJira` (`jiraSdkFetch.ts`), as the invoking user. No
 * resolver hop needed — see `workspaceUsers.ts` for the same pattern.
 *
 * Answering "may this user manage meetings here?" takes two Jira reads, and
 * they are deliberately kept separate because they go stale for entirely
 * different reasons.
 *
 * {@link resolveMeetingPermissionKeys} asks `GET /permissions` for the site's
 * permission catalogue and finds the app's two entries by the `name` declared in
 * the manifest. Jira does not echo back the bare manifest `key`, so matching on
 * `name` is the resolution mechanism — stable regardless of any internal key
 * prefixing. Its answer is a property of the deployed manifest: identical for
 * every user and every project, and only a redeploy can change it. It is
 * therefore cached indefinitely under a build-scoped key
 * ({@link MEETING_PERMISSION_KEYS_STALE_TIME_MS}), so one resolution serves the
 * whole site for the life of the deployment.
 *
 * {@link checkMeetingPermission} then asks `GET /mypermissions` whether the
 * invoking user holds those two keys in one project. That answer changes when an
 * administrator edits the project's permission scheme — outside this app, with
 * no signal to invalidate on. It is cached for
 * {@link MEETING_PERMISSION_CHECK_STALE_TIME_MS}, five minutes: long enough for
 * a burst of iframes and modals around a single interaction to share one read,
 * short enough that a scheme edit takes effect within the few minutes an
 * administrator would expect. Cutting these reads is worth the care, because
 * Jira meters Users/Groups/Permissions in a more expensive rate-limit tier than
 * ordinary reads.
 *
 * Both reads revalidate against a stored `ETag` when Jira offers one
 * (`jiraETag.ts`), so an expired check usually costs a `304` rather than a
 * re-read.
 *
 * Neither step ever degrades a failure into "no permissions". Callers render a
 * `NoPermissionState` from an empty result, so a transient Jira error must
 * surface as an error and not as a confident denial.
 */
import { getAllPermissions, getMyPermissions } from '@smiskinext/sdks-jira';
import { revalidatedJiraRead } from './jiraETag';
import { jiraCallOptions } from './jiraSdkFetch';

export interface MeetingPermissionResult {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

/** Jira's real permission-key strings for the app's custom permissions. */
export interface MeetingPermissionKeys {
    viewKey: string;
    editKey: string;
}

const VIEW_MEETING_PERMISSION_NAME = 'View Meeting';
const EDIT_MEETING_PERMISSION_NAME = 'Edit Meeting';

const ONE_MINUTE_MS = 60 * 1000;

/**
 * How long a resolved key pair stays fresh. Never, in practice: its cache key
 * carries the build version, so a redeploy replaces the entry instead of
 * expiring it.
 */
export const MEETING_PERMISSION_KEYS_STALE_TIME_MS = Number.POSITIVE_INFINITY;

/**
 * How long one user's permission check in one project stays fresh. See the
 * module comment for why five minutes.
 */
export const MEETING_PERMISSION_CHECK_STALE_TIME_MS = 5 * ONE_MINUTE_MS;

const ALL_PERMISSIONS_RESOURCE = 'all-permissions';

interface JiraPermissions {
    permissions?: Record<string, { name?: string; havePermission?: boolean }>;
}

function describeStatus(response: Response | undefined): string {
    return response ? ` (status ${response.status})` : '';
}

/**
 * Resolves the Jira permission keys for the app's declared `view-meeting` /
 * `edit-meeting` custom permissions by matching the names from `manifest.yml`
 * against the site's permission catalogue.
 *
 * Not scoped to a user or a project: the catalogue is site-wide, so one
 * resolution is reusable everywhere until the app is redeployed.
 */
export async function resolveMeetingPermissionKeys(): Promise<MeetingPermissionKeys> {
    const { value, error, response } = await revalidatedJiraRead<
        JiraPermissions,
        MeetingPermissionKeys
    >(
        ALL_PERMISSIONS_RESOURCE,
        (headers) => getAllPermissions({ ...jiraCallOptions, headers }),
        (data) => {
            const entries = Object.entries(data?.permissions ?? {});
            const viewKey = entries.find(
                ([, permission]) =>
                    permission.name === VIEW_MEETING_PERMISSION_NAME,
            )?.[0];
            const editKey = entries.find(
                ([, permission]) =>
                    permission.name === EDIT_MEETING_PERMISSION_NAME,
            )?.[0];

            if (!viewKey || !editKey) {
                throw new Error(
                    'View Meeting / Edit Meeting permissions are not registered on this '
                        + 'Jira site yet — deploy the app and confirm the jira:projectPermission '
                        + 'module is installed.',
                );
            }

            return { viewKey, editKey };
        },
    );

    if (error) {
        throw error instanceof Error
            ? error
            : new Error(
                  'Jira permission lookup failed while listing permissions'
                      + `${describeStatus(response)}.`,
              );
    }
    if (!value) {
        throw new Error(
            'Jira returned no permission catalogue to resolve the meeting '
                + 'permission keys from.',
        );
    }

    return value;
}

/**
 * Checks whether the invoking user holds the resolved meeting permissions in
 * one project, via `GET /rest/api/3/mypermissions`.
 *
 * `accountId` identifies the invoking user for the revalidation cache only — the
 * request itself is always scoped to whoever `requestJira` runs as. Including it
 * keeps a stored validator from ever being replayed for a different user.
 */
export async function checkMeetingPermission(
    projectKey: string,
    accountId: string,
    keys: MeetingPermissionKeys,
): Promise<MeetingPermissionResult> {
    const { viewKey, editKey } = keys;
    const { value, error, response } = await revalidatedJiraRead<
        JiraPermissions,
        MeetingPermissionResult
    >(
        `my-permissions:${accountId}:${projectKey}`,
        (headers) =>
            getMyPermissions({
                ...jiraCallOptions,
                headers,
                query: {
                    projectKey,
                    permissions: `${viewKey},${editKey}`,
                },
            }),
        (data) => {
            const permissions = data?.permissions ?? {};
            return {
                hasViewMeeting: Boolean(permissions[viewKey]?.havePermission),
                hasEditMeeting: Boolean(permissions[editKey]?.havePermission),
            };
        },
    );

    if (error) {
        throw error instanceof Error
            ? error
            : new Error(
                  `Jira permission check failed${describeStatus(response)}.`,
              );
    }
    if (!value) {
        throw new Error(
            `Jira returned no permission check result${describeStatus(response)}.`,
        );
    }

    return value;
}
