import { describe, expect, it } from 'vitest';
import type { Meeting } from './meeting';
import { resolveMeetingHostNames } from './meeting';
import type { ProjectMember } from './projectMember';

describe('resolveMeetingHostNames', () => {
    const currentUser: ProjectMember = {
        accountId: 'jira-current-user',
        displayName: 'Current Jira User',
    };
    const projectMembers: ProjectMember[] = [
        { accountId: 'jira-member-1', displayName: 'Project Member' },
    ];

    const baseMeeting: Meeting = {
        id: 'm-1',
        title: 'Standup',
        projectId: 'project-smiski',
        projectKey: 'SMISKI',
        issueId: 'issue-SMISKI-1',
        issueKey: 'SMISKI-1',
        creatorId: 'jira-member-1',
        creatorName: 'Unknown host',
        hostId: 'jira-member-1',
        hostName: 'Unknown host',
        status: 'RUNNING',
        participantCount: 1,
    };

    it('fills in the list summary placeholder from the Jira directory', () => {
        const [resolved] = resolveMeetingHostNames(
            [baseMeeting],
            [currentUser, ...projectMembers],
        );

        expect(resolved.hostName).toBe('Project Member');
        expect(resolved.creatorName).toBe('Project Member');
    });

    it('keeps the placeholder when the host is not in the directory', () => {
        const [resolved] = resolveMeetingHostNames(
            [baseMeeting],
            [currentUser],
        );

        expect(resolved.hostName).toBe('Unknown host');
    });

    it('refreshes a stale detail-sourced name against the latest directory', () => {
        const meeting: Meeting = {
            ...baseMeeting,
            hostName: 'Old Display Name',
            creatorName: 'Old Display Name',
        };

        const [resolved] = resolveMeetingHostNames(
            [meeting],
            [currentUser, ...projectMembers],
        );

        expect(resolved.hostName).toBe('Project Member');
    });
});
