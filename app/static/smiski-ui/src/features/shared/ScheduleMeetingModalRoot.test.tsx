// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Meeting } from '../../domain';
import type { ScheduleMeetingModalContext } from '../../utils/scheduleMeetingModalContext';

const closeModal = vi.hoisted(() => vi.fn());

vi.mock('@forge/bridge', () => ({
    view: { close: closeModal },
}));

vi.mock('../../components/shared', () => ({
    EditMeetingModal: (props: {
        meetingId: string;
        chrome: string;
        onSaved: () => void;
    }) => (
        <div
            data-testid='edit-meeting-modal'
            data-meeting-id={props.meetingId}
            data-chrome={props.chrome}
        >
            <button type='button' onClick={props.onSaved}>
                Save edit
            </button>
        </div>
    ),
    ScheduleMeetingModal: (props: { chrome: string }) => (
        <div data-testid='schedule-meeting-modal' data-chrome={props.chrome} />
    ),
}));

import { ScheduleMeetingModalRoot } from './ScheduleMeetingModalRoot';

const MEETING: Meeting = {
    id: 'meeting-1',
    title: 'Instant meeting',
    projectId: 'project-1',
    projectKey: 'SMISKI',
    issueId: '10001',
    issueKey: 'SMISKI-1',
    creatorId: 'host-1',
    creatorName: 'Host',
    hostId: 'host-1',
    hostName: 'Host',
    status: 'RUNNING',
    participantCount: 1,
};

function context(meeting?: Meeting): ScheduleMeetingModalContext {
    return {
        kind: 'schedule-meeting',
        issueId: '10001',
        issueKey: 'SMISKI-1',
        projectKey: 'SMISKI',
        meeting,
    };
}

describe('ScheduleMeetingModalRoot', () => {
    afterEach(() => {
        cleanup();
        closeModal.mockReset();
    });

    it('uses the shared embedded edit flow for an Issue Panel meeting', () => {
        render(<ScheduleMeetingModalRoot payload={context(MEETING)} />);

        const modal = screen.getByTestId('edit-meeting-modal');
        expect(modal.dataset.meetingId).toBe(MEETING.id);
        expect(modal.dataset.chrome).toBe('embedded');
        expect(screen.queryByTestId('schedule-meeting-modal')).toBeNull();

        fireEvent.click(screen.getByRole('button', { name: 'Save edit' }));
        expect(closeModal).toHaveBeenCalledWith({
            submitted: true,
            meetingId: MEETING.id,
        });
    });

    it('keeps the embedded schedule flow for new meetings', () => {
        render(<ScheduleMeetingModalRoot payload={context()} />);

        expect(
            screen.getByTestId('schedule-meeting-modal').dataset.chrome,
        ).toBe('embedded');
        expect(screen.queryByTestId('edit-meeting-modal')).toBeNull();
    });
});
