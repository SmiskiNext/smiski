import { describe, expect, it } from 'vitest';
import type { Participant, ProjectMember } from '../domain';
import { resolveParticipantDisplayNames } from './participants';

describe('resolveParticipantDisplayNames', () => {
  const currentUser: ProjectMember = {
    accountId: 'jira-current-user',
    displayName: 'Current Jira User',
  };
  const projectMembers: ProjectMember[] = [
    { accountId: 'jira-member-1', displayName: 'Project Member' },
  ];

  it('replaces stored account-id fallbacks with Jira display names', () => {
    const participants: Participant[] = [
      { accountId: 'jira-current-user', displayName: 'Jordan Avery', role: 'HOST' },
      { accountId: 'jira-member-1', displayName: 'jira-member-1', role: 'PARTICIPANT' },
    ];

    expect(resolveParticipantDisplayNames(participants, currentUser, projectMembers)).toEqual([
      { accountId: 'jira-current-user', displayName: 'Current Jira User', role: 'HOST' },
      { accountId: 'jira-member-1', displayName: 'Project Member', role: 'PARTICIPANT' },
    ]);
  });

  it('keeps the stored name when a user is no longer in the project directory', () => {
    const participants: Participant[] = [
      { accountId: 'former-member', displayName: 'Former Member', role: 'PARTICIPANT' },
    ];

    expect(resolveParticipantDisplayNames(participants, currentUser, projectMembers)).toEqual(
      participants,
    );
  });
});
