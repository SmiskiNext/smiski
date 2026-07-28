import { describe, expect, it } from 'vitest';
import type { Meeting } from '../../domain';
import {
    instantModalPayloadFor,
    shouldWarnBeforeInstant,
} from './startInstantGate';

const runningMeeting = { id: 'm-1', status: 'RUNNING' } as Meeting;

describe('Issue Panel start-instant gate', () => {
    it('opens the form prefilled for the current issue, deriving the project key', () => {
        expect(instantModalPayloadFor('SMISKI-101')).toEqual({
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
    });

    it('prefers an explicit project key when supplied', () => {
        expect(instantModalPayloadFor('SMISKI-101', 'OTHER')).toEqual({
            issueKey: 'SMISKI-101',
            projectKey: 'OTHER',
        });
    });

    it('requires the host-conflict warning before the form when a meeting is running', () => {
        expect(shouldWarnBeforeInstant(runningMeeting)).toBe(true);
    });

    it('skips the warning and opens the form directly when there is no conflict', () => {
        expect(shouldWarnBeforeInstant(null)).toBe(false);
    });
});
