/**
 * useRoomToken — mints a LiveKit room access token for the current user +
 * meeting, via the (temporary, prototype-only) resolver shim in
 * `src/index.ts`. Disabled in standalone `vite dev` — there is no Forge
 * bridge to reach outside a real Forge tunnel/deployment, so there is no
 * meaningful mock to fall back to (see `useLiveKitRoom.ts` for how the
 * meeting room degrades gracefully when this is disabled).
 */
import { useQuery } from '@tanstack/react-query';
import { getRoomToken } from '../api/meetings';
import { queryKeys } from './queryKeys';

export interface UseRoomTokenResult {
    token: string | null;
    url: string | null;
    loading: boolean;
    error: Error | null;
}

export function useRoomToken(
    meetingId?: string,
    enabled = true,
): UseRoomTokenResult {
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.roomToken(meetingId)
            : ['room-token', 'none'],
        queryFn: () => getRoomToken(meetingId as string),
        enabled: enabled && Boolean(meetingId) && !import.meta.env.DEV,
        staleTime: Infinity,
        retry: false,
    });

    return {
        token: query.data?.token ?? null,
        url: query.data?.url ?? null,
        loading: query.isLoading,
        error: query.error as Error | null,
    };
}
