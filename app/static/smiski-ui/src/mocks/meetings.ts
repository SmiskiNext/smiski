import type { Meeting, MeetingStatus } from '../domain';
import { CURRENT_USER, MOCK_USERS } from './users';

const HOUR = 60 * 60 * 1000;
const now = Date.now();

function userName(accountId: string): string {
    return (
        MOCK_USERS.find((user) => user.accountId === accountId)?.displayName ??
        accountId
    );
}

interface MeetingFixture {
    id: string;
    issueKey: string;
    title: string;
    description?: string;
    status: MeetingStatus;
    hostId: string;
    scheduledAt?: string;
    startedAt?: string;
    endedAt?: string;
    participantCount: number;
}

function createMeeting(fixture: MeetingFixture): Meeting {
    return {
        ...fixture,
        projectId: 'project-smiski',
        projectKey: 'SMISKI',
        issueId: `issue-${fixture.issueKey}`,
        issueSummary: fixture.title,
        creatorId: fixture.hostId,
        creatorName: userName(fixture.hostId),
        hostName: userName(fixture.hostId),
    };
}

export const MOCK_PROJECT_KEY = 'SMISKI';

export const INITIAL_MOCK_MEETINGS: Meeting[] = [
    createMeeting({
        id: 'm-101-running',
        issueKey: 'SMISKI-101',
        title: 'Sprint blocker triage',
        description: 'Walk through the blocked tickets before standup.',
        status: 'RUNNING',
        hostId: 'acc-alex-chen',
        startedAt: new Date(now - 12 * 60 * 1000).toISOString(),
        participantCount: 3,
    }),
    createMeeting({
        id: 'm-101-completed-1',
        issueKey: 'SMISKI-101',
        title: 'Root cause review',
        status: 'COMPLETED',
        hostId: CURRENT_USER.accountId,
        scheduledAt: new Date(now - 26 * HOUR).toISOString(),
        startedAt: new Date(now - 26 * HOUR).toISOString(),
        endedAt: new Date(now - 25 * HOUR).toISOString(),
        participantCount: 2,
    }),
    createMeeting({
        id: 'm-102-scheduled-1',
        issueKey: 'SMISKI-102',
        title: 'Design walkthrough with QA',
        description:
            'Review the new onboarding flow before implementation starts.',
        status: 'SCHEDULED',
        hostId: CURRENT_USER.accountId,
        scheduledAt: new Date(now + 4 * HOUR).toISOString(),
        participantCount: 3,
    }),
    createMeeting({
        id: 'm-102-scheduled-2',
        issueKey: 'SMISKI-102',
        title: 'Stakeholder demo',
        status: 'SCHEDULED',
        hostId: 'acc-bao-tran',
        scheduledAt: new Date(now + 26 * HOUR).toISOString(),
        participantCount: 2,
    }),
    createMeeting({
        id: 'm-102-scheduled-3',
        issueKey: 'SMISKI-102',
        title: 'Retro follow-up',
        status: 'SCHEDULED',
        hostId: CURRENT_USER.accountId,
        scheduledAt: new Date(now + 72 * HOUR).toISOString(),
        participantCount: 1,
    }),
    createMeeting({
        id: 'm-102-completed-1',
        issueKey: 'SMISKI-102',
        title: 'Initial scoping call',
        status: 'COMPLETED',
        hostId: 'acc-chi-nguyen',
        scheduledAt: new Date(now - 96 * HOUR).toISOString(),
        startedAt: new Date(now - 96 * HOUR).toISOString(),
        endedAt: new Date(now - 95 * HOUR).toISOString(),
        participantCount: 2,
    }),
    createMeeting({
        id: 'm-102-canceled-1',
        issueKey: 'SMISKI-102',
        title: 'Vendor sync (postponed)',
        status: 'CANCELED',
        hostId: 'acc-sara-kim',
        scheduledAt: new Date(now - 10 * HOUR).toISOString(),
        participantCount: 0,
    }),
    createMeeting({
        id: 'm-55-running-host-conflict',
        issueKey: 'SMISKI-55',
        title: 'Incident bridge — payments outage',
        status: 'RUNNING',
        hostId: CURRENT_USER.accountId,
        startedAt: new Date(now - 5 * 60 * 1000).toISOString(),
        participantCount: 3,
    }),
    createMeeting({
        id: 'm-201-completed-recorded',
        issueKey: 'SMISKI-201',
        title: 'Architecture review — auth bridge spike',
        status: 'COMPLETED',
        hostId: 'acc-minh-le',
        scheduledAt: new Date(now - 50 * HOUR).toISOString(),
        startedAt: new Date(now - 50 * HOUR).toISOString(),
        endedAt: new Date(now - 49 * HOUR).toISOString(),
        participantCount: 3,
    }),
    createMeeting({
        id: 'm-202-completed-failed-recording',
        issueKey: 'SMISKI-202',
        title: 'Customer feedback session',
        status: 'COMPLETED',
        hostId: 'acc-diego-alvarez',
        startedAt: new Date(now - 8 * HOUR).toISOString(),
        endedAt: new Date(now - 7 * HOUR).toISOString(),
        participantCount: 2,
    }),
];
