/**
 * Mints LiveKit Cloud room access tokens directly from the resolver (demo
 * branch — bypasses the `meet` backend, which normally owns this). Reads
 * `LIVEKIT_URL`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET` from `manifest.yml`'s
 * `environment.variables` (the key/secret have no default — see
 * app/AGENTS.md's "Manual steps" for `forge variables set --encrypt`).
 *
 * Every participant gets the same publish/subscribe grant: `useLiveKitRoom.ts`
 * already hardcodes `role: 'PARTICIPANT'` for everyone, and screen-share is
 * just another published track, not a separate LiveKit grant.
 */
import { AccessToken } from 'livekit-server-sdk';

const TOKEN_TTL_SECONDS = 60 * 60 * 6; // 6h — comfortably longer than a meeting

export interface LiveKitToken {
    token: string;
    url: string;
}

export async function mintLiveKitToken(
    roomName: string,
    accountId: string,
    displayName: string,
): Promise<LiveKitToken> {
    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    const url = process.env.LIVEKIT_URL;
    if (!apiKey || !apiSecret || !url) {
        throw new Error(
            'LiveKit is not configured: set LIVEKIT_API_KEY/LIVEKIT_API_SECRET ' +
                '(forge variables set --encrypt) and LIVEKIT_URL for this environment.',
        );
    }

    const accessToken = new AccessToken(apiKey, apiSecret, {
        identity: accountId,
        name: displayName,
        ttl: TOKEN_TTL_SECONDS,
    });
    accessToken.addGrant({
        room: roomName,
        roomJoin: true,
        canPublish: true,
        canSubscribe: true,
        canPublishData: true,
    });

    return { token: await accessToken.toJwt(), url };
}
