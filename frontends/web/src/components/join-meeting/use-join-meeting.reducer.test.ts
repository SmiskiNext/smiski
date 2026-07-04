import { describe, expect, it } from 'vitest';
import type { JoinState } from './use-join-meeting.ts';
import { joinReducer } from './use-join-meeting.ts';

const initialState: JoinState = { phase: 'IDLE' };

describe('joinReducer', () => {
    describe('LOOKUP_STARTED', () => {
        it('transitions from IDLE to LOOKING_UP', () => {
            const next = joinReducer(initialState, { type: 'LOOKUP_STARTED' });
            expect(next).toEqual({ phase: 'LOOKING_UP' });
        });
    });

    describe('LOOKUP_NEEDS_PASSWORD', () => {
        it('transitions to NEEDS_PASSWORD with meetingId and title', () => {
            const next = joinReducer(initialState, {
                type: 'LOOKUP_NEEDS_PASSWORD',
                meetingId: 'mid-1',
                title: 'Team Sync',
            });
            expect(next).toEqual({
                phase: 'NEEDS_PASSWORD',
                meetingId: 'mid-1',
                title: 'Team Sync',
            });
        });
    });

    describe('LOOKUP_READY', () => {
        it('transitions to REQUESTING phase', () => {
            const next = joinReducer(initialState, {
                type: 'LOOKUP_READY',
                meetingId: 'mid-1',
                title: 'Standup',
            });
            expect(next).toEqual({
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Standup',
            });
        });
    });

    describe('LOOKUP_FAILED', () => {
        it('transitions to ERROR with retryable flag', () => {
            const next = joinReducer(initialState, {
                type: 'LOOKUP_FAILED',
                message: 'Network error',
                retryable: true,
            });
            expect(next).toEqual({
                phase: 'ERROR',
                message: 'Network error',
                retryable: true,
            });
        });

        it('transitions to ERROR with non-retryable flag', () => {
            const next = joinReducer(initialState, {
                type: 'LOOKUP_FAILED',
                message: 'Meeting not found',
                retryable: false,
            });
            expect(next).toEqual({
                phase: 'ERROR',
                message: 'Meeting not found',
                retryable: false,
            });
        });
    });

    describe('REQUEST_STARTED', () => {
        it('transitions from NEEDS_PASSWORD to REQUESTING', () => {
            const needsPasswordState: JoinState = {
                phase: 'NEEDS_PASSWORD',
                meetingId: 'mid-1',
                title: 'Team Sync',
            };
            const next = joinReducer(needsPasswordState, {
                type: 'REQUEST_STARTED',
            });
            expect(next).toEqual({
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Team Sync',
            });
        });

        it('stays in IDLE when REQUEST_STARTED is dispatched', () => {
            const next = joinReducer(initialState, { type: 'REQUEST_STARTED' });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });

    describe('REQUEST_APPROVED', () => {
        it('transitions to APPROVED with token and roomName', () => {
            const requestingState: JoinState = {
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Daily',
            };
            const next = joinReducer(requestingState, {
                type: 'REQUEST_APPROVED',
                token: 'tok-abc',
                roomName: 'daily-standup',
                meetingId: 'mid-1',
            });
            expect(next).toEqual({
                phase: 'APPROVED',
                token: 'tok-abc',
                roomName: 'daily-standup',
                meetingId: 'mid-1',
            });
        });
    });

    describe('REQUEST_PENDING', () => {
        it('transitions from REQUESTING to WAITING_APPROVAL', () => {
            const requestingState: JoinState = {
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Q&A',
            };
            const next = joinReducer(requestingState, {
                type: 'REQUEST_PENDING',
                requestId: 'req-99',
            });
            expect(next).toEqual({
                phase: 'WAITING_APPROVAL',
                meetingId: 'mid-1',
                requestId: 'req-99',
                title: 'Q&A',
            });
        });

        it('ignores REQUEST_PENDING when not in REQUESTING phase', () => {
            const next = joinReducer(initialState, {
                type: 'REQUEST_PENDING',
                requestId: 'req-99',
            });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });

    describe('REQUEST_DENIED', () => {
        it('returns to NEEDS_PASSWORD when INVALID_PASSWORD denied from REQUESTING', () => {
            const requestingState: JoinState = {
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Team Sync',
            };
            const next = joinReducer(requestingState, {
                type: 'REQUEST_DENIED',
                reason: 'INVALID_PASSWORD',
            });
            expect(next).toEqual({
                phase: 'NEEDS_PASSWORD',
                meetingId: 'mid-1',
                title: 'Team Sync',
                error: 'INVALID_PASSWORD',
            });
        });

        it('transitions to DENIED for non-password denial from REQUESTING', () => {
            const requestingState: JoinState = {
                phase: 'REQUESTING',
                meetingId: 'mid-1',
                title: 'Team Sync',
            };
            const next = joinReducer(requestingState, {
                type: 'REQUEST_DENIED',
                reason: 'GUEST_NOT_ALLOWED',
            });
            expect(next).toEqual({
                phase: 'DENIED',
                reason: 'GUEST_NOT_ALLOWED',
            });
        });
    });

    describe('SSE_APPROVED', () => {
        it('transitions to APPROVED', () => {
            const waitingState: JoinState = {
                phase: 'WAITING_APPROVAL',
                meetingId: 'mid-1',
                requestId: 'req-99',
                title: 'All Hands',
            };
            const next = joinReducer(waitingState, {
                type: 'SSE_APPROVED',
                token: 'tok-sse',
                roomName: 'all-hands',
                meetingId: 'mid-1',
            });
            expect(next).toEqual({
                phase: 'APPROVED',
                token: 'tok-sse',
                roomName: 'all-hands',
                meetingId: 'mid-1',
            });
        });
    });

    describe('SSE_DENIED', () => {
        it('returns to NEEDS_PASSWORD when INVALID_PASSWORD from WAITING_APPROVAL', () => {
            const waitingState: JoinState = {
                phase: 'WAITING_APPROVAL',
                meetingId: 'mid-1',
                requestId: 'req-99',
                title: 'Design Review',
            };
            const next = joinReducer(waitingState, {
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

        it('transitions to DENIED for other reasons from WAITING_APPROVAL', () => {
            const waitingState: JoinState = {
                phase: 'WAITING_APPROVAL',
                meetingId: 'mid-1',
                requestId: 'req-99',
                title: 'Design Review',
            };
            const next = joinReducer(waitingState, {
                type: 'SSE_DENIED',
                reason: 'MEETING_FULL',
            });
            expect(next).toEqual({
                phase: 'DENIED',
                reason: 'MEETING_FULL',
            });
        });
    });

    describe('SSE_EXPIRED', () => {
        it('transitions to EXPIRED', () => {
            const waitingState: JoinState = {
                phase: 'WAITING_APPROVAL',
                meetingId: 'mid-1',
                requestId: 'req-99',
                title: 'Design Review',
            };
            const next = joinReducer(waitingState, { type: 'SSE_EXPIRED' });
            expect(next).toEqual({ phase: 'EXPIRED' });
        });
    });

    describe('SSE_FAILED', () => {
        it('transitions to ERROR with retryable flag', () => {
            const next = joinReducer(initialState, {
                type: 'SSE_FAILED',
                message: 'Connection lost',
                retryable: true,
            });
            expect(next).toEqual({
                phase: 'ERROR',
                message: 'Connection lost',
                retryable: true,
            });
        });
    });

    describe('RETRY', () => {
        it('resets to IDLE from any phase', () => {
            const errorState: JoinState = {
                phase: 'ERROR',
                message: 'Server unreachable',
                retryable: true,
            };
            const next = joinReducer(errorState, { type: 'RETRY' });
            expect(next).toEqual({ phase: 'IDLE' });
        });

        it('resets from EXPIRED to IDLE', () => {
            const next = joinReducer({ phase: 'EXPIRED' }, { type: 'RETRY' });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });

    describe('unknown action', () => {
        it('returns the current state unchanged', () => {
            const next = joinReducer(initialState, {
                type: 'NONEXISTENT_ACTION' as never,
            });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });
});
