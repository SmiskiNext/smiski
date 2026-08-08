/**
 * useLiveKitRoom — connects to a LiveKit room (given a token+url from
 * `useRoomToken`), publishes local mic/camera, and exposes a participant
 * list shaped for `ParticipantVideoTile`/`ParticipantVideoGrid`.
 *
 * `role` is hardcoded to 'PARTICIPANT' for every entry here: LiveKit has no
 * concept of meeting "host" — that lives in the backend meeting roster, which
 * this hook does not read. Callers that need the HOST badge resolve it from
 * that roster instead of from the live LiveKit participant list.
 */

import {
    type Participant as LKParticipant,
    type LocalAudioTrack,
    type LocalVideoTrack,
    type RemoteAudioTrack,
    type RemoteVideoTrack,
    Room,
    RoomEvent,
    type RoomOptions,
    Track,
    VideoPreset,
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
    /** True while LiveKit's audio-level detection marks this participant as talking. */
    isSpeaking: boolean;
    videoTrack: LocalVideoTrack | RemoteVideoTrack | null;
    audioTrack: LocalAudioTrack | RemoteAudioTrack | null;
}

/**
 * Streaming profile applied to every published track in the room.
 *
 * `adaptiveStream` (subscriber-side quality matching) and `dynacast`
 * (publisher-side pausing of unconsumed simulcast layers) only pay off when
 * the publisher actually offers layers to choose from, so the two simulcast
 * layers below are what make the pair effective: the SFU can serve a 180p
 * thumbnail to a filmstrip tile instead of the full 720p stream.
 *
 * `videoSimulcastLayers` is typed as `VideoPreset` instances rather than plain
 * `{ width, height, encoding }` objects — the class carries a derived
 * `aspectRatio`/`resolution`, so a literal does not satisfy it.
 *
 * `red` (redundant audio data) trades roughly 5% bandwidth for far fewer
 * dropout artifacts, and `dtx` claws that back by not transmitting during
 * silence — worth pairing in a meeting where most participants are muted
 * listeners.
 */
const ROOM_OPTIONS: RoomOptions = {
    adaptiveStream: true,
    dynacast: true,
    publishDefaults: {
        videoCodec: 'vp8',
        videoEncoding: { maxBitrate: 1_500_000, maxFramerate: 30 },
        videoSimulcastLayers: [
            new VideoPreset({
                width: 640,
                height: 360,
                maxBitrate: 500_000,
                maxFramerate: 20,
            }),
            new VideoPreset({
                width: 320,
                height: 180,
                maxBitrate: 150_000,
                maxFramerate: 15,
            }),
        ],
        screenShareEncoding: { maxBitrate: 3_000_000, maxFramerate: 30 },
        red: true,
        dtx: true,
    },
    stopLocalTrackOnUnpublish: true,
    disconnectOnPageLeave: true,
};

/**
 * The one screen-share track currently being presented in the room, if any.
 *
 * LiveKit publishes a screen share as a separate `Track.Source.ScreenShare`
 * track rather than replacing the sharer's camera, so it needs its own slot —
 * reading only `Camera`/`Microphone` (as `toParticipant` does) makes a shared
 * screen invisible even though it is being published. Only the first sharer
 * found is surfaced: the UI presents one screen at a time, like Google Meet.
 */
export interface ScreenShareFeed {
    track: LocalVideoTrack | RemoteVideoTrack;
    participantName: string;
    isLocal: boolean;
}

export type LiveKitConnectionState =
    | 'idle'
    | 'connecting'
    | 'connected'
    | 'disconnected'
    | 'error';

/**
 * Another participant arriving in or leaving the room, as reported by
 * LiveKit's `ParticipantConnected`/`ParticipantDisconnected` events.
 *
 * Never describes the local user: LiveKit raises neither event for the local
 * participant, and `ParticipantConnected` only covers participants who join
 * after the local user — so a room that is already busy on entry produces no
 * events at all.
 */
export interface ParticipantPresenceEvent {
    kind: 'joined' | 'left';
    displayName: string;
}

export interface UseLiveKitRoomOptions {
    token: string | null;
    url: string | null;
    enabled: boolean;
    /**
     * Notified whenever another participant joins or leaves. Held in a ref, so
     * passing a new function identity on every render never re-runs the
     * connect effect (which would tear the room down and rejoin it).
     */
    onParticipantPresence?: (event: ParticipantPresenceEvent) => void;
}

export interface UseLiveKitRoomResult {
    connectionState: LiveKitConnectionState;
    error: Error | null;
    localAccountId: string | null;
    participants: LiveMeetingParticipant[];
    /** Non-null while someone (possibly the local user) is presenting. */
    screenShare: ScreenShareFeed | null;
    isMicOn: boolean;
    isCameraOn: boolean;
    isScreenSharing: boolean;
    /**
     * Identity of the participant LiveKit currently ranks loudest, or `null`
     * while the room is silent. Drives the spotlight layout's subject when
     * nothing is pinned and nobody is presenting.
     */
    activeSpeakerId: string | null;
    /**
     * User-facing note about a media-permission change: either the most
     * recent `toggleScreenShare()` call being rejected (disabled by the
     * host, or the OS share picker was cancelled), or the host revoking
     * mic/camera/screen-share access mid-session via meeting settings.
     * Cleared on the next toggle attempt.
     */
    mediaNotice: string | null;
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
        isSpeaking: participant.isSpeaking,
        videoTrack:
            (videoPub?.track as LocalVideoTrack | RemoteVideoTrack | undefined)
            ?? null,
        audioTrack:
            (audioPub?.track as LocalAudioTrack | RemoteAudioTrack | undefined)
            ?? null,
    };
}

export type RevocableSource = 'microphone' | 'camera' | 'screenShare';

/**
 * `canPublishSources`' element type, taken structurally off `Participant.permissions`
 * instead of importing `TrackSource`/`@livekit/protocol` directly — that package is
 * only a transitive dependency of `livekit-client` (not in package.json), and this
 * avoids adding it just for a type annotation.
 */
type CanPublishSources = NonNullable<
    LKParticipant['permissions']
>['canPublishSources'];

/**
 * Compares two raw `canPublishSources` snapshots (as carried by
 * `RoomEvent.ParticipantPermissionsChanged`) and returns which of our three
 * user-facing publish groups were present before and are missing after — i.e.
 * newly revoked by the host mid-session, via `Track.sourceFromProto` (public
 * off `livekit-client`, decodes the protobuf `TrackSource` values). Folds
 * `Track.Source.ScreenShareAudio` into `'screenShare'`: the backend's
 * `allowScreenShare` setting drives both sources together, so a separate
 * "audio" notice would be meaningless.
 */
export function diffRevokedPublishSources(
    prevSources: CanPublishSources,
    nextSources: CanPublishSources,
): RevocableSource[] {
    const toGroup = (
        source: CanPublishSources[number],
    ): RevocableSource | undefined => {
        switch (Track.sourceFromProto(source)) {
            case Track.Source.Microphone:
                return 'microphone';
            case Track.Source.Camera:
                return 'camera';
            case Track.Source.ScreenShare:
            case Track.Source.ScreenShareAudio:
                return 'screenShare';
            default:
                return undefined;
        }
    };
    const prevGroups = new Set(prevSources.map(toGroup).filter(Boolean));
    const nextGroups = new Set(nextSources.map(toGroup).filter(Boolean));
    return (['microphone', 'camera', 'screenShare'] as const).filter(
        (group) => prevGroups.has(group) && !nextGroups.has(group),
    );
}

const REVOKED_SOURCE_LABEL: Record<RevocableSource, string> = {
    microphone: 'microphone',
    camera: 'camera',
    screenShare: 'screen sharing',
};

/** Builds the host-revoked-permissions banner copy; `[]` in ⇒ `''` out (caller skips). */
export function describeRevokedSources(sources: RevocableSource[]): string {
    if (sources.length === 0) return '';
    const labels = sources.map((source) => REVOKED_SOURCE_LABEL[source]);
    const joined =
        labels.length === 1
            ? labels[0]
            : labels.length === 2
              ? `${labels[0]} and ${labels[1]}`
              : `${labels.slice(0, -1).join(', ')}, and ${labels.at(-1)}`;
    return `The host turned off your ${joined}.`;
}

export function useLiveKitRoom({
    token,
    url,
    enabled,
    onParticipantPresence,
}: UseLiveKitRoomOptions): UseLiveKitRoomResult {
    const roomRef = useRef<Room | null>(null);
    const onParticipantPresenceRef = useRef(onParticipantPresence);
    useEffect(() => {
        onParticipantPresenceRef.current = onParticipantPresence;
    }, [onParticipantPresence]);
    const [connectionState, setConnectionState] =
        useState<LiveKitConnectionState>('idle');
    const [error, setError] = useState<Error | null>(null);
    const [participants, setParticipants] = useState<LiveMeetingParticipant[]>(
        [],
    );
    const [screenShare, setScreenShare] = useState<ScreenShareFeed | null>(
        null,
    );
    // Mirrored into state rather than read off `roomRef` during render: a ref
    // mutation doesn't re-render, so a render-time read leaves the "Share
    // screen" button stuck on its previous state.
    const [isScreenSharing, setIsScreenSharing] = useState(false);
    const [activeSpeakerId, setActiveSpeakerId] = useState<string | null>(null);
    const [mediaNotice, setMediaNotice] = useState<string | null>(null);

    const snapshot = useCallback(() => {
        const room = roomRef.current;
        if (!room) return;
        const local = toParticipant(room.localParticipant, true);
        const remotes = Array.from(room.remoteParticipants.values()).map((p) =>
            toParticipant(p, false),
        );
        setParticipants([local, ...remotes]);

        // Local participant first, so the presenter sees their own screen
        // immediately rather than waiting on a round trip through the server.
        const candidates: [LKParticipant, boolean][] = [
            [room.localParticipant, true],
            ...Array.from(room.remoteParticipants.values()).map(
                (p): [LKParticipant, boolean] => [p, false],
            ),
        ];
        const feed = candidates.reduce<ScreenShareFeed | null>(
            (found, [participant, isLocal]) => {
                if (found) return found;
                const track = participant.getTrackPublication(
                    Track.Source.ScreenShare,
                )?.track as LocalVideoTrack | RemoteVideoTrack | undefined;
                return track
                    ? {
                          track,
                          participantName:
                              participant.name || participant.identity,
                          isLocal,
                      }
                    : null;
            },
            null,
        );
        setScreenShare(feed);
        setIsScreenSharing(room.localParticipant.isScreenShareEnabled);
    }, []);

    useEffect(() => {
        if (!enabled || !token || !url) return;

        const room = new Room(ROOM_OPTIONS);
        roomRef.current = room;
        let cancelled = false;

        const notifyPresence = (
            kind: ParticipantPresenceEvent['kind'],
            participant: LKParticipant,
        ) => {
            onParticipantPresenceRef.current?.({
                kind,
                displayName: participant.name || participant.identity,
            });
        };

        room.on(RoomEvent.ParticipantConnected, (participant) => {
            snapshot();
            notifyPresence('joined', participant);
        })
            .on(RoomEvent.ParticipantDisconnected, (participant) => {
                snapshot();
                notifyPresence('left', participant);
            })
            .on(RoomEvent.TrackSubscribed, snapshot)
            .on(RoomEvent.TrackUnsubscribed, snapshot)
            .on(RoomEvent.TrackMuted, snapshot)
            .on(RoomEvent.TrackUnmuted, snapshot)
            .on(RoomEvent.LocalTrackPublished, snapshot)
            .on(RoomEvent.LocalTrackUnpublished, snapshot)
            // LiveKit reports speakers loudest-first, so the head of the list
            // is the active speaker. `snapshot()` runs alongside it because
            // each participant's `isSpeaking` flag changed too, and the tiles
            // read that off the participant list rather than off this id.
            .on(RoomEvent.ActiveSpeakersChanged, (speakers) => {
                setActiveSpeakerId(speakers[0]?.identity ?? null);
                snapshot();
            })
            .on(
                RoomEvent.ParticipantPermissionsChanged,
                (prevPermissions, participant) => {
                    if (participant !== room.localParticipant) return;
                    const nextPermissions = room.localParticipant.permissions;
                    if (!nextPermissions) return;
                    const revoked = diffRevokedPublishSources(
                        prevPermissions?.canPublishSources ?? [],
                        nextPermissions.canPublishSources,
                    );
                    if (revoked.length === 0) return;

                    setMediaNotice(describeRevokedSources(revoked));

                    void (async () => {
                        const stops: Promise<unknown>[] = [];
                        if (
                            revoked.includes('microphone')
                            && room.localParticipant.isMicrophoneEnabled
                        ) {
                            stops.push(
                                room.localParticipant.setMicrophoneEnabled(
                                    false,
                                ),
                            );
                        }
                        if (
                            revoked.includes('camera')
                            && room.localParticipant.isCameraEnabled
                        ) {
                            stops.push(
                                room.localParticipant.setCameraEnabled(false),
                            );
                        }
                        if (
                            revoked.includes('screenShare')
                            && room.localParticipant.isScreenShareEnabled
                        ) {
                            stops.push(
                                room.localParticipant.setScreenShareEnabled(
                                    false,
                                ),
                            );
                        }
                        try {
                            await Promise.all(stops);
                        } catch (stopError) {
                            console.warn(
                                '[useLiveKitRoom] failed to stop a revoked track',
                                stopError,
                            );
                        }
                        if (cancelled) return;
                        snapshot();
                    })();
                },
            )
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
        const isOn = room.localParticipant.isMicrophoneEnabled;
        setMediaNotice(null);
        try {
            await room.localParticipant.setMicrophoneEnabled(!isOn);
            snapshot();
        } catch (micError) {
            // Rejected by LiveKit — the host has disabled microphone access
            // for this meeting (MeetingSettings.allowMicrophone) — or by the
            // browser/OS denying mic permission.
            console.warn('[useLiveKitRoom] microphone toggle failed', micError);
            setMediaNotice(
                isOn
                    ? 'Could not turn off your microphone.'
                    : "Couldn't turn on your microphone. It may be disabled for this meeting.",
            );
        }
    }, [snapshot]);

    const toggleCamera = useCallback(async () => {
        const room = roomRef.current;
        if (!room) return;
        const isOn = room.localParticipant.isCameraEnabled;
        setMediaNotice(null);
        try {
            await room.localParticipant.setCameraEnabled(!isOn);
            snapshot();
        } catch (cameraError) {
            // Rejected by LiveKit — the host has disabled video for this
            // meeting (MeetingSettings.allowVideo) — or by the browser/OS
            // denying camera permission.
            console.warn('[useLiveKitRoom] camera toggle failed', cameraError);
            setMediaNotice(
                isOn
                    ? 'Could not turn off your camera.'
                    : "Couldn't turn on your camera. It may be disabled for this meeting.",
            );
        }
    }, [snapshot]);

    const toggleScreenShare = useCallback(async () => {
        const room = roomRef.current;
        if (!room) return;
        const isSharing = room.localParticipant.isScreenShareEnabled;
        setMediaNotice(null);
        try {
            await room.localParticipant.setScreenShareEnabled(!isSharing);
            snapshot();
        } catch (shareError) {
            // Rejected either by LiveKit (screen share disabled for this
            // meeting — see MeetingSettings.allowScreenShare) or by the
            // browser (user cancelled the share picker). Swallow it here so
            // it doesn't surface as an unhandled rejection; the button click
            // that triggered this already reflects the failure visually.
            console.warn(
                '[useLiveKitRoom] screen share toggle failed',
                shareError,
            );
            setMediaNotice(
                isSharing
                    ? 'Could not stop screen sharing.'
                    : "Couldn't start screen sharing. It may be disabled for this meeting.",
            );
        }
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
        screenShare,
        isMicOn: local?.isMicOn ?? false,
        isCameraOn: local?.isCameraOn ?? false,
        isScreenSharing,
        activeSpeakerId,
        mediaNotice,
        toggleMic,
        toggleCamera,
        toggleScreenShare,
        leave,
    };
}
