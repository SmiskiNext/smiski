// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    readMeetingLayoutMode,
    writeMeetingLayoutMode,
} from './meetingLayoutPreference';

const STORAGE_KEY = 'smiski:meeting-layout';

describe('meeting layout preference', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('defaults to auto when nothing has been stored', () => {
        expect(readMeetingLayoutMode()).toBe('auto');
    });

    it('round-trips a tiled preference', () => {
        writeMeetingLayoutMode('tiled');

        expect(localStorage.getItem(STORAGE_KEY)).toBe('tiled');
        expect(readMeetingLayoutMode()).toBe('tiled');
    });

    it('round-trips a spotlight preference', () => {
        writeMeetingLayoutMode('spotlight');

        expect(readMeetingLayoutMode()).toBe('spotlight');
    });

    it('round-trips back to auto', () => {
        writeMeetingLayoutMode('spotlight');
        writeMeetingLayoutMode('auto');

        expect(readMeetingLayoutMode()).toBe('auto');
    });

    it('treats an unrecognized stored value as auto', () => {
        localStorage.setItem(STORAGE_KEY, 'not-a-layout');

        expect(readMeetingLayoutMode()).toBe('auto');
    });

    it('falls back to auto when reading storage throws', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('SecurityError');
        });

        expect(readMeetingLayoutMode()).toBe('auto');
    });

    it('degrades silently when writing storage throws', () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('QuotaExceededError');
        });

        expect(() => writeMeetingLayoutMode('tiled')).not.toThrow();
    });
});
