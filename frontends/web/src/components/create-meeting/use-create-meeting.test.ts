import { describe, expect, it } from 'vitest';
import {
    type CreateMeetingState,
    createMeetingReducer,
} from './use-create-meeting.ts';

const initialState: CreateMeetingState = { phase: 'IDLE' };

describe('createMeetingReducer', () => {
    describe('CREATE_STARTED', () => {
        it('transitions from IDLE to CREATING', () => {
            const next = createMeetingReducer(initialState, {
                type: 'CREATE_STARTED',
            });
            expect(next).toEqual({ phase: 'CREATING' });
        });

        it('transitions from ERROR to CREATING', () => {
            const errorState: CreateMeetingState = {
                phase: 'ERROR',
                message: 'Network error',
                retryable: true,
            };
            const next = createMeetingReducer(errorState, {
                type: 'CREATE_STARTED',
            });
            expect(next).toEqual({ phase: 'CREATING' });
        });
    });

    describe('CREATE_SUCCEEDED', () => {
        it('transitions from CREATING to READY with meetingId and shortCode', () => {
            const creatingState: CreateMeetingState = { phase: 'CREATING' };
            const next = createMeetingReducer(creatingState, {
                type: 'CREATE_SUCCEEDED',
                meetingId: 'mtg-123',
                shortCode: 'ABC123',
            });
            expect(next).toEqual({
                phase: 'READY',
                meetingId: 'mtg-123',
                shortCode: 'ABC123',
            });
        });
    });

    describe('FAILED — from CREATING phase', () => {
        it('transitions to ERROR with retryable flag on network failure', () => {
            const creatingState: CreateMeetingState = { phase: 'CREATING' };
            const next = createMeetingReducer(creatingState, {
                type: 'FAILED',
                message: 'Network error',
                retryable: true,
            });
            expect(next).toEqual({
                phase: 'ERROR',
                message: 'Network error',
                retryable: true,
            });
        });

        it('transitions to ERROR with non-retryable flag on business failure', () => {
            const creatingState: CreateMeetingState = { phase: 'CREATING' };
            const next = createMeetingReducer(creatingState, {
                type: 'FAILED',
                message: 'Meeting was created but returned no identifier.',
                retryable: false,
            });
            expect(next).toEqual({
                phase: 'ERROR',
                message: 'Meeting was created but returned no identifier.',
                retryable: false,
            });
        });
    });

    describe('RETRY', () => {
        it('resets from ERROR to IDLE', () => {
            const errorState: CreateMeetingState = {
                phase: 'ERROR',
                message: 'Server unreachable',
                retryable: true,
            };
            const next = createMeetingReducer(errorState, { type: 'RETRY' });
            expect(next).toEqual({ phase: 'IDLE' });
        });

        it('resets from CREATING to IDLE', () => {
            const creatingState: CreateMeetingState = { phase: 'CREATING' };
            const next = createMeetingReducer(creatingState, { type: 'RETRY' });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });

    describe('RESET', () => {
        it('resets from READY to IDLE', () => {
            const readyState: CreateMeetingState = {
                phase: 'READY',
                meetingId: 'mtg-123',
                shortCode: 'ABC123',
            };
            const next = createMeetingReducer(readyState, { type: 'RESET' });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });

    describe('unknown action', () => {
        it('returns the current state unchanged', () => {
            const next = createMeetingReducer(initialState, {
                type: 'NONEXISTENT_ACTION' as never,
            });
            expect(next).toEqual({ phase: 'IDLE' });
        });
    });
});
