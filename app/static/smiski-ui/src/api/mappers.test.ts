import { describe, expect, it } from 'vitest';
import { permissionsFromBackend } from './mappers';

describe('backend API mappers', () => {
    it('maps permission envelopes flexibly', () => {
        expect(
            permissionsFromBackend({
                canViewMeeting: true,
                hasEditMeeting: false,
            }),
        ).toEqual({
            hasViewMeeting: true,
            hasEditMeeting: false,
        });
    });
});
