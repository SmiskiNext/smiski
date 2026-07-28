/**
 * Forge resolver — backend entry point (STUB).
 *
 * Responsibility (future): thin bridge between the Custom UI frontend and the
 * Kong Gateway. Each resolver reads the invoking Jira user's context, forwards
 * the request to the backend (tenant/meet/record services) with the user's
 * identity, and returns the result. It holds NO business logic itself — the
 * "business brain" lives in the backend `meeting-management` service.
 *
 * NOTE: Every definition below is an unimplemented placeholder. No fetch, no
 * auth bridging, no data shaping is wired up yet. Do not add business logic
 * here — see architecture_vi.md (Forge Remote / JWT-JWKS auth bridge).
 */
import Resolver from '@forge/resolver';
import type {
    MeetCreateInstantMeetingRequest,
    MeetInvitee,
    MeetSettings,
} from '@smiskinext/smiski-ts';
import { createInstant } from '@smiskinext/smiski-ts';
import { AccessToken } from 'livekit-server-sdk';
import { searchUsers } from './jiraSdkClient';
import { createMeetClient } from './meetSdkClient';

const resolver = new Resolver();

const INSTANT_MEETING_API_VERSION = 1;

const DEFAULT_MEETING_SETTINGS: MeetSettings = {
    admissionPolicy: 'OPEN',
    maxParticipants: 50,
    allowScreenShare: true,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
};

/** Invoke payload sent by the frontend to create an instant meeting. */
interface CreateInstantMeetingPayload {
    title: string;
    description: string;
    issueKey: string;
    issueId?: string;
    projectKey?: string;
    zoneId: string;
    host: {
        displayName: string;
        deviceId: string;
        avatarUrl?: string | null;
    };
    organizerEmail: string;
    organizerDisplayName: string;
    invitees?: MeetInvitee[];
    settings?: Partial<MeetSettings>;
}

function buildInstantMeetingRequest(
    payload: CreateInstantMeetingPayload,
): MeetCreateInstantMeetingRequest {
    return {
        title: payload.title,
        description: payload.description,
        issueLink: {
            issueId: payload.issueId,
            issueKey: payload.issueKey,
            projectKey: payload.projectKey ?? payload.issueKey.split('-')[0],
        },
        settings: { ...DEFAULT_MEETING_SETTINGS, ...payload.settings },
        host: {
            displayName: payload.host.displayName,
            deviceId: payload.host.deviceId,
            avatarUrl: payload.host.avatarUrl ?? undefined,
        },
        organizerEmail: payload.organizerEmail,
        organizerDisplayName: payload.organizerDisplayName,
        zoneId: payload.zoneId,
        invitees: payload.invitees ?? [],
    };
}

// TODO(UC02/UC07): list meetings for the current Issue.
resolver.define('getIssueMeetings', async (_req) => {
    throw new Error('Not implemented: getIssueMeetings');
});

/**
 * Search Jira site (workspace) users as the invoking user. Identity is derived
 * from the Forge context, never from the browser, so Jira enforces the user's
 * own "Browse users" permission and a rejection (e.g. 403) surfaces as an error
 * rather than a user list.
 */
resolver.define('searchWorkspaceUsers', async (req) => {
    const query = (req.payload?.query as string | undefined) ?? '';
    return searchUsers(query);
});

/**
 * Create + auto-start an instant meeting through the `meet` backend SDK. The
 * tenant/account headers are attached server-side from the Forge context; there
 * is no mock fallback, so a backend rejection surfaces to the caller.
 */
resolver.define('createInstantMeeting', async (req) => {
    const payload = req.payload as unknown as CreateInstantMeetingPayload;

    const tenantId = req.context.cloudId as string | undefined;
    const accountId = req.context.accountId as string | undefined;
    if (!tenantId || !accountId) {
        throw new Error(
            'createInstantMeeting: missing tenant/account identity in context.',
        );
    }

    const client = createMeetClient({ tenantId, accountId });
    const result = await createInstant({
        client,
        body: buildInstantMeetingRequest(payload),
        path: { version: INSTANT_MEETING_API_VERSION },
    });

    if (result.error) {
        const problem = result.error as {
            detail?: string;
            title?: string;
            code?: string;
            traceId?: string;
        };
        const message =
            problem.detail
            ?? problem.title
            ?? 'The meeting backend rejected the instant-create request.';
        const error = new Error(message) as Error & {
            code?: string;
            traceId?: string;
        };
        error.code = problem.code;
        error.traceId = problem.traceId;
        throw error;
    }

    return result.data ?? {};
});

// TODO(UC03): create a scheduled meeting bound to the current Issue.
resolver.define('scheduleMeeting', async (_req) => {
    throw new Error('Not implemented: scheduleMeeting');
});

// TODO: list/search meetings across a project (project page dashboard).
resolver.define('getProjectMeetings', async (_req) => {
    throw new Error('Not implemented: getProjectMeetings');
});

// TODO: resolve the current user's permission (VIEW_MEETING / EDIT_MEETING).
resolver.define('getMeetingPermission', async (_req) => {
    throw new Error('Not implemented: getMeetingPermission');
});

// ⚠️ TEMPORARY PROTOTYPE SHIM — mints the LiveKit JWT directly in this
// resolver using livekit-server-sdk (pure local JWT signing, no network
// call), bypassing the designed Kong → `meet` service flow described in
// architecture.vi.md, which does not exist yet. Replace this with a call to
// the real `meet` service's token-issuance endpoint once it exists, and
// remove this comment block when that happens.
//
// No authorization check against a real meeting/participant roster is done
// here — any user who can open this app can mint a token for any
// `meetingId` string. Acceptable for this prototype only.
resolver.define('getRoomToken', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    if (!meetingId) throw new Error('getRoomToken: meetingId is required');

    const accountId = req.context.accountId as string | undefined;
    if (!accountId)
        throw new Error('getRoomToken: no invoking user accountId in context');

    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    const url = process.env.LIVEKIT_URL;
    if (!apiKey || !apiSecret || !url) {
        throw new Error(
            'getRoomToken: LIVEKIT_API_KEY / LIVEKIT_API_SECRET / LIVEKIT_URL not configured (forge variables set)',
        );
    }

    const accessToken = new AccessToken(apiKey, apiSecret, {
        identity: accountId,
        ttl: '4h',
    });
    accessToken.addGrant({
        roomJoin: true,
        room: meetingId,
        canPublish: true,
        canSubscribe: true,
    });
    const token = await accessToken.toJwt();

    return { token, url };
});

export const handler = resolver.getDefinitions();
