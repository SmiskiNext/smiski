/**
 * useLiveKitRoom — connects to a LiveKit room (given a token+url from
 * `useRoomToken`), publishes local mic/camera, and exposes a participant
 * list shaped for `ParticipantVideoTile`/`ParticipantVideoGrid`.
 *
 * `role` is hardcoded to 'PARTICIPANT' for every entry here: LiveKit has no
 * concept of meeting "host" — that lives in the (still-mocked) meeting
 * roster, whose accountIds won't line up with real Jira accountIds during a
 * live trial. The HOST badge simply won't render for real participants;
 * that's an accepted prototype simplification, not a bug.
 */

import {
    type Participant as LKParticipant,
    type LocalAudioTrack,
    type LocalVideoTrack,
    type RemoteAudioTrack,
    type RemoteVideoTrack,
    Room,
    RoomEvent,
    Track,
} from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ParticipantRole } from '../domain';

export interface LiveMeetingParticipant {
    accountId: string;
    displayName: string;
    role: ParticipantRole;
    isLocal: boolean;
    isMicOn: boolean;
    isCameraOn: boolean;
    videoTrack: LocalVideoTrack | RemoteVideoTrack | null;
    audioTrack: LocalAudioTrack | RemoteAudioTrack | null;
}

export type LiveKitConnectionState =
    'idle' | 'connecting' | 'connected' | 'disconnected' | 'error';

export interface UseLiveKitRoomOptions {
    token: string | null;
    url: string | null;
    enabled: boolean;
}

export interface UseLiveKitRoomResult {
    connectionState: LiveKitConnectionState;
    error: Error | null;
    localAccountId: string | null;
    participants: LiveMeetingParticipant[];
    isMicOn: boolean;
    isCameraOn: boolean;
    isScreenSharing: boolean;
    toggleMic: () => Promise<void>;
    toggleCamera: () => Promise<void>;
    toggleScreenShare: () => Promise<void>;
    leave: () => Promise<void>;
}

function toParticipant(
    participant: LKParticipant,
    isLocal: boolean,
): LiveMeetingParticipant {
    const videoPub = participant.getTrackPublication(Track.Source.Camera);
    const audioPub = participant.getTrackPublication(Track.Source.Microphone);
    return {
        accountId: participant.identity,
        displayName: participant.name || participant.identity,
        role: 'PARTICIPANT',
        isLocal,
        isMicOn: participant.isMicrophoneEnabled,
        isCameraOn: participant.isCameraEnabled,
        videoTrack:
            (videoPub?.track as
                LocalVideoTrack | RemoteVideoTrack | undefined) ?? null,
        audioTrack:
            (audioPub?.track as
                LocalAudioTrack | RemoteAudioTrack | undefined) ?? null,
    };
}

export function useLiveKitRoom({
    token,
    url,
    enabled,
}: UseLiveKitRoomOptions): UseLiveKitRoomResult {
    const roomRef = useRef<Room | null>(null);
    const [connectionState, setConnectionState] =
        useState<LiveKitConnectionState>('idle');
    const [error, setError] = useState<Error | null>(null);
    const [participants, setParticipants] = useState<LiveMeetingParticipant[]>(
        [],
    );

    const snapshot = useCallback(() => {
        const room = roomRef.current;
        if (!room) return;
        const local = toParticipant(room.localParticipant, true);
        const remotes = Array.from(room.remoteParticipants.values()).map((p) =>
            toParticipant(p, false),
        );
        setParticipants([local, ...remotes]);
    }, []);

    useEffect(() => {
        if (!enabled || !token || !url) return;

        const room = new Room({ adaptiveStream: true, dynacast: true });
        roomRef.current = room;
        let cancelled = false;

        room.on(RoomEvent.ParticipantConnected, snapshot)
            .on(RoomEvent.ParticipantDisconnected, snapshot)
            .on(RoomEvent.TrackSubscribed, snapshot)
            .on(RoomEvent.TrackUnsubscribed, snapshot)
            .on(RoomEvent.TrackMuted, snapshot)
            .on(RoomEvent.TrackUnmuted, snapshot)
            .on(RoomEvent.LocalTrackPublished, snapshot)
            .on(RoomEvent.LocalTrackUnpublished, snapshot)
            .on(RoomEvent.Disconnected, () => {
                if (!cancelled) setConnectionState('disconnected');
            });

        (async () => {
            try {
                setConnectionState('connecting');
                await room.connect(url, token);
                if (cancelled) return;
                try {
                    await Promise.all([
                        room.localParticipant.setMicrophoneEnabled(true),
                        room.localParticipant.setCameraEnabled(true),
                    ]);
                } catch (mediaError) {
                    // Camera/mic permission denied or unavailable — join audio/video-off
                    // rather than blocking the whole room. See plan Risk #1.
                    console.warn(
                        '[useLiveKitRoom] camera/mic unavailable',
                        mediaError,
                    );
                }
                if (cancelled) return;
                snapshot();
                setConnectionState('connected');
            } catch (connectError) {
                if (!cancelled) {
                    setError(connectError as Error);
                    setConnectionState('error');
                }
            }
        })();

        return () => {
            cancelled = true;
            void room.disconnect();
            roomRef.current = null;
        };
    }, [enabled, token, url, snapshot]);

    const toggleMic = useCallback(async () => {
        const room = roomRef.current;
        if (!room) return;
        await room.localParticipant.setMicrophoneEnabled(
            !room.localParticipant.isMicrophoneEnabled,
        );
        snapshot();
    }, [snapshot]);

    const toggleCamera = useCallback(async () => {
        const room = roomRef.current;
        if (!room) return;
        await room.localParticipant.setCameraEnabled(
            !room.localParticipant.isCameraEnabled,
        );
        snapshot();
    }, [snapshot]);

    const toggleScreenShare = useCallback(async () => {
        const room = roomRef.current;
        if (!room) return;
        const isSharing = room.localParticipant.isScreenShareEnabled;
        await room.localParticipant.setScreenShareEnabled(!isSharing);
        snapshot();
    }, [snapshot]);

    const leave = useCallback(async () => {
        await roomRef.current?.disconnect();
        roomRef.current = null;
    }, []);

    const local = participants.find((p) => p.isLocal);

    return {
        connectionState,
        error,
        localAccountId: local?.accountId ?? null,
        participants,
        isMicOn: local?.isMicOn ?? false,
        isCameraOn: local?.isCameraOn ?? false,
        isScreenSharing:
            roomRef.current?.localParticipant.isScreenShareEnabled ?? false,
        toggleMic,
        toggleCamera,
        toggleScreenShare,
        leave,
    };
}
