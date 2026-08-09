import { describe, expect, it } from 'vitest';
import {
    MEETING_TITLE_MAX_LENGTH,
    meetingDescriptionError,
    meetingEmailError,
    meetingTimeRangeError,
    meetingTitleError,
} from './meetingFormValidation';

describe('meeting form validation', () => {
    it('rejects blank and overlong titles using the OpenAPI limit', () => {
        expect(meetingTitleError('   ')).toBe('Enter a meeting title.');
        expect(
            meetingTitleError('x'.repeat(MEETING_TITLE_MAX_LENGTH + 1)),
        ).toBe('Title must be 255 characters or fewer.');
        expect(meetingTitleError('Sprint planning')).toBeUndefined();
    });

    it('requires a non-blank description', () => {
        expect(meetingDescriptionError('')).toBe(
            'Enter a meeting description.',
        );
        expect(meetingDescriptionError('Agenda')).toBeUndefined();
    });

    it('requires real organizer and invitee email addresses', () => {
        expect(meetingEmailError(undefined, [])).toContain(
            'does not expose an email address',
        );
        expect(meetingEmailError('host@example.com', [{ email: '' }])).toBe(
            'Remove invitees whose Jira email address is unavailable.',
        );
        expect(
            meetingEmailError('host@example.com', [
                { email: 'invitee@example.com' },
            ]),
        ).toBeUndefined();
    });

    it('requires a future, chronological time range', () => {
        const now = Date.parse('2026-08-01T00:00:00.000Z');
        expect(meetingTimeRangeError('', '2026-08-01T02:00:00.000Z', now)).toBe(
            'Choose a valid start date and time.',
        );
        expect(meetingTimeRangeError('2026-08-01T02:00:00.000Z', '', now)).toBe(
            'Choose a valid end date and time.',
        );
        expect(
            meetingTimeRangeError(
                '2026-07-31T23:00:00.000Z',
                '2026-08-01T02:00:00.000Z',
                now,
            ),
        ).toBe('Choose a start date and time in the future.');
        expect(
            meetingTimeRangeError(
                '2026-08-01T02:00:00.000Z',
                '2026-08-01T01:00:00.000Z',
                now,
            ),
        ).toBe('End time must be later than start time.');
        expect(
            meetingTimeRangeError(
                '2026-08-01T02:00:00.000Z',
                '2026-08-01T03:00:00.000Z',
                now,
            ),
        ).toBeUndefined();
    });
});
