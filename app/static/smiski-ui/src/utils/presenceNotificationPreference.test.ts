// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    readPresenceNotificationsEnabled,
    writePresenceNotificationsEnabled,
} from './presenceNotificationPreference';

const STORAGE_KEY = 'smiski:presence-notifications';

describe('presence notification preference', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('defaults to enabled when nothing has been stored', () => {
        expect(readPresenceNotificationsEnabled()).toBe(true);
    });

    it('round-trips a disabled preference', () => {
        writePresenceNotificationsEnabled(false);

        expect(localStorage.getItem(STORAGE_KEY)).toBe('false');
        expect(readPresenceNotificationsEnabled()).toBe(false);
    });

    it('round-trips an enabled preference', () => {
        writePresenceNotificationsEnabled(false);
        writePresenceNotificationsEnabled(true);

        expect(readPresenceNotificationsEnabled()).toBe(true);
    });

    it('treats an unrecognized stored value as enabled', () => {
        localStorage.setItem(STORAGE_KEY, 'not-a-boolean');

        expect(readPresenceNotificationsEnabled()).toBe(true);
    });

    it('falls back to enabled when reading storage throws', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('SecurityError');
        });

        expect(readPresenceNotificationsEnabled()).toBe(true);
    });

    it('degrades silently when writing storage throws', () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('QuotaExceededError');
        });

        expect(() => writePresenceNotificationsEnabled(false)).not.toThrow();
    });
});
