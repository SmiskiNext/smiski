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
import { AccessToken } from 'livekit-server-sdk';

const resolver = new Resolver();

// TODO(UC02/UC07): list meetings for the current Issue.
resolver.define('getIssueMeetings', async (_req) => {
  throw new Error('Not implemented: getIssueMeetings');
});

// TODO(UC01): create an instant meeting bound to the current Issue.
resolver.define('createInstantMeeting', async (_req) => {
  throw new Error('Not implemented: createInstantMeeting');
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
  if (!accountId) throw new Error('getRoomToken: no invoking user accountId in context');

  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;
  const url = process.env.LIVEKIT_URL;
  if (!apiKey || !apiSecret || !url) {
    throw new Error(
      'getRoomToken: LIVEKIT_API_KEY / LIVEKIT_API_SECRET / LIVEKIT_URL not configured (forge variables set)',
    );
  }

  const accessToken = new AccessToken(apiKey, apiSecret, { identity: accountId, ttl: '4h' });
  accessToken.addGrant({ roomJoin: true, room: meetingId, canPublish: true, canSubscribe: true });
  const token = await accessToken.toJwt();

  return { token, url };
});

export const handler = resolver.getDefinitions();
