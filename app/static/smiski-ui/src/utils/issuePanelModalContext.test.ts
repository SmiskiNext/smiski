import { describe, expect, it } from 'vitest';
import {
    ACTIVE_MEETING_WARNING_MODAL_KIND,
    isIssuePanelModalContext,
    MEETING_DETAIL_MODAL_KIND,
    MEETING_SETTINGS_MODAL_KIND,
} from './issuePanelModalContext';

describe('isIssuePanelModalContext', () => {
    it.each([
        MEETING_DETAIL_MODAL_KIND,
        ACTIVE_MEETING_WARNING_MODAL_KIND,
        MEETING_SETTINGS_MODAL_KIND,
    ])('recognizes the %s platform modal', (kind) => {
        expect(isIssuePanelModalContext({ kind })).toBe(true);
    });

    it('rejects unrelated and malformed Forge modal contexts', () => {
        expect(isIssuePanelModalContext({ kind: 'schedule-meeting' })).toBe(
            false,
        );
        expect(isIssuePanelModalContext({})).toBe(false);
        expect(isIssuePanelModalContext(null)).toBe(false);
    });
});
