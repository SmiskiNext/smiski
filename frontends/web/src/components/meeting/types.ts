import type { LocalParticipant, RemoteParticipant } from 'livekit-client';

export type ParticipantRole = 'HOST' | 'PARTICIPANT' | 'GUEST';

export type ParticipantViewModel = {
    identity: string;
    displayName: string;
    avatarUrl?: string;
    isMicEnabled: boolean;
    isCameraEnabled: boolean;
    isLocal: boolean;
    role?: ParticipantRole;
    livekitParticipant: LocalParticipant | RemoteParticipant;
};
