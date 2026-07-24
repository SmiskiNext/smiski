import { describe, expect, it } from 'vitest';
import { listProjectMeetings } from './db';

describe('listProjectMeetings issue filtering', () => {
    it('returns meetings for the selected Jira issue key', async () => {
        const meetings = await listProjectMeetings({
            projectKey: 'SMISKI',
            issueKey: 'SMISKI-102',
        });

        expect(meetings.length).toBeGreaterThan(0);
        expect(
            meetings.every((meeting) => meeting.issueKey === 'SMISKI-102'),
        ).toBe(true);
    });

    it('does not treat a partial issue key as a match', async () => {
        const meetings = await listProjectMeetings({
            projectKey: 'SMISKI',
            issueKey: 'SMISKI-10',
        });

        expect(meetings).toEqual([]);
    });
});
