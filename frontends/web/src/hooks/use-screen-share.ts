'use client';

import {
    useLocalParticipant,
    useRemoteParticipants,
} from '@livekit/components-react';
import { Track } from 'livekit-client';
import { useCallback, useMemo, useState } from 'react';

type ActiveSharer = {
    identity: string;
    name: string | null;
} | null;

function hasScreenSharePublication(participant: {
    trackPublications: Map<string, { source?: Track.Source }>;
}): boolean {
    return [...participant.trackPublications.values()].some(
        (publication) => publication.source === Track.Source.ScreenShare,
    );
}

export function useScreenShare() {
    const { isScreenShareEnabled, localParticipant } = useLocalParticipant();
    const remoteParticipants = useRemoteParticipants();
    const [error, setError] = useState<Error | null>(null);

    const isLocalSharing = isScreenShareEnabled;

    const remoteSharer = useMemo(
        () =>
            remoteParticipants.find((participant) =>
                hasScreenSharePublication(participant),
            ) ?? null,
        [remoteParticipants],
    );

    const activeSharer: ActiveSharer = isLocalSharing
        ? {
              identity: localParticipant.identity,
              name: localParticipant.name ?? null,
          }
        : remoteSharer
          ? {
                identity: remoteSharer.identity,
                name: remoteSharer.name ?? null,
            }
          : null;

    const permissions = localParticipant.permissions;
    const canPublishSources = permissions?.canPublishSources;
    const isHostRole =
        permissions?.canPublish === true
        && (!canPublishSources || canPublishSources.length === 0);
    const canShareScreen =
        isHostRole
        && (activeSharer === null
            || activeSharer.identity === localParticipant.identity);

    const isHostSharing = useMemo(() => {
        if (isLocalSharing && isHostRole) return true;
        if (!remoteSharer) return false;
        return (
            remoteSharer.permissions?.canPublish === true
            && (!remoteSharer.permissions?.canPublishSources
                || remoteSharer.permissions.canPublishSources.length === 0)
        );
    }, [isLocalSharing, isHostRole, remoteSharer]);

    const toggle = useCallback(async () => {
        setError(null);
        try {
            await localParticipant.setScreenShareEnabled(!isLocalSharing);
            return null;
        } catch (caught) {
            const nextError =
                caught instanceof Error
                    ? caught
                    : new Error('Unable to toggle screen share');
            setError(nextError);
            return nextError;
        }
    }, [isLocalSharing, localParticipant]);

    return {
        activeSharer,
        canShareScreen,
        error,
        isHostSharing,
        isLocalSharing,
        toggle,
    };
}
