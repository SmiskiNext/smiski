import { describe, expect, it } from 'vitest';
import type { JoinState } from './use-join-meeting.ts';
import { joinReducer } from './use-join-meeting.ts';

describe('approval handoff behavior', () => {
    it('REQUEST_APPROVED transitions to APPROVED from REQUESTING', () => {
        const requesting: JoinState = {
            phase: 'REQUESTING',
            meetingId: 'mid-1',
            title: 'Town Hall',
        };
        const next = joinReducer(requesting, {
            type: 'REQUEST_APPROVED',
            token: 'tok-direct',
            roomName: 'town-hall-123',
            meetingId: 'mid-1',
        });
        expect(next).toEqual({
            phase: 'APPROVED',
            token: 'tok-direct',
            roomName: 'town-hall-123',
            meetingId: 'mid-1',
        });
    });

    it('SSE_APPROVED transitions to APPROVED from WAITING_APPROVAL', () => {
        const waiting: JoinState = {
            phase: 'WAITING_APPROVAL',
            meetingId: 'mid-1',
            requestId: 'req-42',
            title: 'Design Review',
        };
        const next = joinReducer(waiting, {
            type: 'SSE_APPROVED',
            token: 'tok-sse-99',
            roomName: 'design-review-room',
            meetingId: 'mid-1',
        });
        expect(next).toEqual({
            phase: 'APPROVED',
            token: 'tok-sse-99',
            roomName: 'design-review-room',
            meetingId: 'mid-1',
        });
    });

    it('denial from WAITING_APPROVAL does not reach APPROVED', () => {
        const waiting: JoinState = {
            phase: 'WAITING_APPROVAL',
            meetingId: 'mid-1',
            requestId: 'req-42',
            title: 'Design Review',
        };
        const next = joinReducer(waiting, {
            type: 'SSE_DENIED',
            reason: 'GUEST_NOT_ALLOWED',
        });
        expect(next.phase).not.toBe('APPROVED');
        expect(next).toEqual({
            phase: 'DENIED',
            reason: 'GUEST_NOT_ALLOWED',
        });
    });

    it('invalid password denial from WAITING_APPROVAL returns to NEEDS_PASSWORD', () => {
        const waiting: JoinState = {
            phase: 'WAITING_APPROVAL',
            meetingId: 'mid-1',
            requestId: 'req-42',
            title: 'Design Review',
        };
        const next = joinReducer(waiting, {
            type: 'SSE_DENIED',
            reason: 'INVALID_PASSWORD',
        });
        expect(next).toEqual({
            phase: 'NEEDS_PASSWORD',
            meetingId: 'mid-1',
            title: 'Design Review',
            error: 'INVALID_PASSWORD',
        });
    });

    it('RETRY resets APPROVED back to IDLE', () => {
        const approved: JoinState = {
            phase: 'APPROVED',
            token: 'tok-any',
            roomName: 'any-room',
            meetingId: 'mid-any',
        };
        const next = joinReducer(approved, { type: 'RETRY' });
        expect(next).toEqual({ phase: 'IDLE' });
    });

    it('SSE_EXPIRED from WAITING_APPROVAL does not reach APPROVED', () => {
        const waiting: JoinState = {
            phase: 'WAITING_APPROVAL',
            meetingId: 'mid-1',
            requestId: 'req-42',
            title: 'Design Review',
        };
        const next = joinReducer(waiting, { type: 'SSE_EXPIRED' });
        expect(next).toEqual({ phase: 'EXPIRED' });
        expect(next.phase).not.toBe('APPROVED');
    });

    it('unrecognised action keeps APPROVED state stable', () => {
        const approved: JoinState = {
            phase: 'APPROVED',
            token: 'tok-abc',
            roomName: 'room-xyz',
            meetingId: 'mid-xyz',
        };
        const next = joinReducer(approved, {
            type: 'FAKE_ACTION' as never,
        });
        expect(next).toEqual({
            phase: 'APPROVED',
            token: 'tok-abc',
            roomName: 'room-xyz',
            meetingId: 'mid-xyz',
        });
    });
});
