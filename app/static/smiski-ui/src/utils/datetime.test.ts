import { describe, expect, it } from 'vitest';
import {
    getLocalTimeZone,
    isoToWallTimeInZone,
    resolveUserTimeZone,
} from './datetime';

describe('resolveUserTimeZone', () => {
    it('passes a valid IANA profile zone through unchanged', () => {
        expect(resolveUserTimeZone('Asia/Ho_Chi_Minh')).toBe(
            'Asia/Ho_Chi_Minh',
        );
    });

    it('falls back to the browser zone for an unresolvable value', () => {
        expect(resolveUserTimeZone('Mars/Phobos')).toBe(getLocalTimeZone());
    });

    it('falls back to the browser zone when the profile zone is missing', () => {
        expect(resolveUserTimeZone(undefined)).toBe(getLocalTimeZone());
    });
});

describe('isoToWallTimeInZone', () => {
    it('formats an instant in the meeting timezone instead of the browser timezone', () => {
        expect(
            isoToWallTimeInZone('2026-08-01T02:30:00.000Z', 'Asia/Ho_Chi_Minh'),
        ).toEqual({ date: '2026-08-01', time: '09:30' });
    });

    it('returns empty fields for an invalid instant', () => {
        expect(isoToWallTimeInZone('invalid', 'UTC')).toEqual({
            date: '',
            time: '',
        });
    });
});
