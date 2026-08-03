/**
 * useRoomToken — mints a LiveKit room access token for the current user +
 * meeting, via the backend `join` operation (`api/meetings.ts`'s
 * `getRoomToken`, built on `joinMeeting`). Disabled in standalone `vite dev`
 * — there is no Forge bridge to reach outside a real Forge tunnel/deployment,
 * so there is no meaningful mock to fall back to (see `useLiveKitRoom.ts` for
 * how the meeting room degrades gracefully when this is disabled). May
 * already be cached by `useStartMeeting`, which seeds this same query key
 * from its own `join` call so the room screen doesn't re-request a token.
 */
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getDeviceId } from '../api/mappers';
import { getRoomToken } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import { queryKeys } from './queryKeys';

export interface UseRoomTokenResult {
    token: string | null;
    url: string | null;
    loading: boolean;
    waitingForApproval: boolean;
    error: Error | null;
}

export function useRoomToken(
    meetingId?: string,
    enabled = true,
): UseRoomTokenResult {
    const currentUser = useCurrentUser();
    const [waitingForApproval, setWaitingForApproval] = useState(false);
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.roomToken(meetingId)
            : ['room-token', 'none'],
        queryFn: async ({ signal }) => {
            setWaitingForApproval(false);
            try {
                return await getRoomToken(
                    meetingId as string,
                    {
                        displayName: currentUser.displayName,
                        deviceId: getDeviceId(),
                        avatarUrl: currentUser.avatarUrl,
                    },
                    {
                        signal,
                        onPending: () => setWaitingForApproval(true),
                    },
                );
            } finally {
                setWaitingForApproval(false);
            }
        },
        enabled: enabled && Boolean(meetingId) && !import.meta.env.DEV,
        staleTime: Infinity,
        retry: false,
    });

    return {
        token: query.data?.token ?? null,
        url: query.data?.url ?? null,
        loading: query.isLoading,
        waitingForApproval,
        error: query.error as Error | null,
    };
}
