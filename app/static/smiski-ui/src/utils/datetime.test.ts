import { describe, expect, it } from 'vitest';
import { getLocalTimeZone, resolveUserTimeZone } from './datetime';

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
