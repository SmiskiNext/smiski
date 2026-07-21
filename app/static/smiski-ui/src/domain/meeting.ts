import type { MeetingStatus } from './enums';

/** Canonical meeting model shared by dashboard policy, API clients, and UI. */
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
}

export interface MeetingPermissions {
  hasViewMeeting: boolean;
  hasEditMeeting: boolean;
  canViewMeeting: boolean;
  canEditMeeting: boolean;
  isLoading: boolean;
  error: Error | null;
}
