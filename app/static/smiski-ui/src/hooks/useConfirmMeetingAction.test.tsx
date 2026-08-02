// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: vi.fn(),
    Modal: vi.fn(),
}));

const cancelMutate = vi.fn();
const endMutate = vi.fn();
const cancelReset = vi.fn();
const endReset = vi.fn();

vi.mock('./useMeetingMutations', () => ({
    useCancelMeeting: () => ({
        mutate: cancelMutate,
        isPending: false,
        error: null,
        reset: cancelReset,
    }),
    useEndMeeting: () => ({
        mutate: endMutate,
        isPending: false,
        error: null,
        reset: endReset,
    }),
}));

import type { Meeting } from '../domain';
import { useConfirmMeetingAction } from './useConfirmMeetingAction';

const MEETING: Meeting = {
    id: 'meeting-1',
    title: 'Sprint planning',
    projectId: 'project-smiski',
    projectKey: 'SMISKI',
    issueId: 'issue-SMISKI-101',
    issueKey: 'SMISKI-101',
    creatorId: 'acc-host',
    creatorName: 'Host User',
    hostId: 'acc-host',
    hostName: 'Host User',
    status: 'RUNNING',
    participantCount: 3,
};

describe('useConfirmMeetingAction (inline presentation)', () => {
    beforeEach(() => {
        cancelMutate.mockReset();
        endMutate.mockReset();
        cancelReset.mockReset();
        endReset.mockReset();
    });

    it('opens a pending confirmation without firing the mutation yet', () => {
        const onDone = vi.fn();
        const { result } = renderHook(() =>
            useConfirmMeetingAction(onDone, 'inline'),
        );

        act(() => result.current.request('CANCEL', MEETING));

        expect(result.current.pending).toEqual({
            action: 'CANCEL',
            meeting: MEETING,
        });
        expect(cancelMutate).not.toHaveBeenCalled();
        expect(endMutate).not.toHaveBeenCalled();
    });

    it('confirms a CANCEL through cancelMeeting and reports success', () => {
        const onDone = vi.fn();
        const { result } = renderHook(() =>
            useConfirmMeetingAction(onDone, 'inline'),
        );

        act(() => result.current.request('CANCEL', MEETING));
        act(() => result.current.confirm());

        expect(cancelMutate).toHaveBeenCalledWith(
            MEETING.id,
            expect.objectContaining({ onSuccess: expect.any(Function) }),
        );
        expect(endMutate).not.toHaveBeenCalled();

        const { onSuccess } = cancelMutate.mock.calls[0][1];
        act(() => onSuccess());

        expect(onDone).toHaveBeenCalledWith({
            appearance: 'success',
            message: 'Meeting canceled.',
        });
        expect(result.current.pending).toBeNull();
    });

    it('confirms an END through endMeeting and reports success', () => {
        const onDone = vi.fn();
        const { result } = renderHook(() =>
            useConfirmMeetingAction(onDone, 'inline'),
        );

        act(() => result.current.request('END', MEETING));
        act(() => result.current.confirm());

        expect(endMutate).toHaveBeenCalledWith(
            MEETING.id,
            expect.objectContaining({ onSuccess: expect.any(Function) }),
        );
        expect(cancelMutate).not.toHaveBeenCalled();

        const { onSuccess } = endMutate.mock.calls[0][1];
        act(() => onSuccess());

        expect(onDone).toHaveBeenCalledWith({
            appearance: 'success',
            message: 'Meeting ended.',
        });
        expect(result.current.pending).toBeNull();
    });

    it('keeps the dialog open on a failed confirm (no onSuccess call, pending stays set)', () => {
        const onDone = vi.fn();
        const { result } = renderHook(() =>
            useConfirmMeetingAction(onDone, 'inline'),
        );

        act(() => result.current.request('CANCEL', MEETING));
        act(() => result.current.confirm());
        // The mutation rejected — react-query would surface it via
        // `mutation.error`, not by invoking `onSuccess`. Since that never
        // fires, `confirm` must not have cleared `pending` on its own.
        expect(result.current.pending).not.toBeNull();
        expect(onDone).not.toHaveBeenCalled();
    });

    it('dismiss clears pending and resets both mutations', () => {
        const onDone = vi.fn();
        const { result } = renderHook(() =>
            useConfirmMeetingAction(onDone, 'inline'),
        );

        act(() => result.current.request('END', MEETING));
        act(() => result.current.dismiss());

        expect(result.current.pending).toBeNull();
        expect(cancelReset).toHaveBeenCalled();
        expect(endReset).toHaveBeenCalled();
    });
});
