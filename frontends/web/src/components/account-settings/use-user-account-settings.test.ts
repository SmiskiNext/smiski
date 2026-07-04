import { afterEach, describe, expect, it } from 'vitest';

function getCookieValue(name: string): string | undefined {
    if (typeof document === 'undefined') return undefined;
    const match = document.cookie
        .split('; ')
        .find((row) => row.startsWith(`${name}=`));
    if (!match) return undefined;
    return match.split('=').slice(1).join('=');
}

describe('getCookieValue', () => {
    const originalDocument = globalThis.document;

    afterEach(() => {
        Object.defineProperty(globalThis, 'document', {
            value: originalDocument,
            writable: true,
            configurable: true,
        });
    });

    it('returns undefined when document is undefined', () => {
        Object.defineProperty(globalThis, 'document', {
            value: undefined,
            writable: true,
            configurable: true,
        });
        expect(getCookieValue('refresh_token')).toBeUndefined();
    });

    it('returns undefined for missing cookie', () => {
        Object.defineProperty(globalThis, 'document', {
            value: {
                cookie: 'access_token=abc123; other=value',
            } as Document,
            writable: true,
            configurable: true,
        });
        expect(getCookieValue('refresh_token')).toBeUndefined();
    });

    it('extracts the correct cookie value', () => {
        Object.defineProperty(globalThis, 'document', {
            value: {
                cookie: 'access_token=abc123; refresh_token=xyz789; next_locale=en',
            } as Document,
            writable: true,
            configurable: true,
        });
        expect(getCookieValue('refresh_token')).toBe('xyz789');
    });

    it('handles cookie with equals sign in value', () => {
        Object.defineProperty(globalThis, 'document', {
            value: {
                cookie: 'refresh_token=abc=xyz=123',
            } as Document,
            writable: true,
            configurable: true,
        });
        expect(getCookieValue('refresh_token')).toBe('abc=xyz=123');
    });
});

describe('AccountSettingsResult type contract', () => {
    it('loading phase has null profile and error', () => {
        const state = {
            phase: 'LOADING' as const,
            profile: null,
            errorMessage: null,
        };
        expect(state.phase).toBe('LOADING');
        expect(state.profile).toBeNull();
        expect(state.errorMessage).toBeNull();
    });

    it('success phase has profile and null error', () => {
        const state = {
            phase: 'SUCCESS' as const,
            profile: { id: 'u1', email: 'test@test.com', fullName: 'Test' },
            errorMessage: null,
        };
        expect(state.phase).toBe('SUCCESS');
        expect(state.profile).not.toBeNull();
        expect(state.errorMessage).toBeNull();
    });

    it('error phase has null profile and error message', () => {
        const state = {
            phase: 'ERROR' as const,
            profile: null,
            errorMessage: 'Network error',
        };
        expect(state.phase).toBe('ERROR');
        expect(state.profile).toBeNull();
        expect(state.errorMessage).toBe('Network error');
    });
});
