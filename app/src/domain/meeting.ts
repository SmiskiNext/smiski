/**
 * Meeting domain types for the resolver-side KVS store (demo branch — see
 * app/src/meetingStore.ts). Field-for-field mirror of
 * `static/smiski-ui/src/domain/meeting.ts` / `domain/participant.ts` /
 * `domain/enums.ts`, duplicated rather than imported: `app/src` is an
 * isolated CommonJS package and the Custom UI is ESM/Vite — there is no
 * cross-package import between them today.
 */

export type MeetingStatus = 'SCHEDULED' | 'RUNNING' | 'COMPLETED' | 'CANCELED';

export type ParticipantRole = 'HOST' | 'PARTICIPANT';

export interface MeetingSettings {
    admissionPolicy: string;
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

export interface Participant {
    accountId: string;
    displayName: string;
    role: ParticipantRole;
    /** ISO datetime the participant joined the room. */
    joinedAt?: string;
    /** ISO datetime the participant left the room. */
    leftAt?: string;
}

/** Canonical meeting model — same shape returned to the Custom UI. */
export interface Meeting {
    id: string;
    title: string;
    description?: string;
    projectId: string;
    projectKey: string;
    issueId: string;
    issueKey: string;
    issueSummary?: string;
    creatorId: string;
    creatorName: string;
    hostId: string;
    hostName: string;
    scheduledAt?: string;
    startedAt?: string;
    endedAt?: string;
    status: MeetingStatus;
    participantCount: number;
    endTime?: string;
    zoneId?: string;
    settings?: MeetingSettings;
}
