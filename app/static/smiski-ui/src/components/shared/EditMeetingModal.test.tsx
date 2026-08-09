// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
    vi,
} from 'vitest';
import type { Meeting } from '../../domain';

const mocks = vi.hoisted(() => ({
    meeting: null as Meeting | null,
    updateMeeting: vi.fn(),
    updateSettings: vi.fn(),
    addInvitees: vi.fn(),
    removeInvitees: vi.fn(),
}));

vi.mock('../../hooks/useMeeting', () => ({
    useMeeting: () => ({
        meeting: mocks.meeting,
        loading: false,
        error: null,
        refetch: vi.fn(),
    }),
}));

vi.mock('../../hooks/useMeetingInvitees', () => ({
    useMeetingInvitees: () => ({
        invitees: [],
        loading: false,
        error: null,
    }),
    useAddMeetingInvitees: () => ({
        mutateAsync: mocks.addInvitees,
        isPending: false,
    }),
    useRemoveMeetingInvitees: () => ({
        mutateAsync: mocks.removeInvitees,
        isPending: false,
    }),
}));

vi.mock('../../hooks/useMeetingMutations', () => ({
    useUpdateMeeting: () => ({
        mutateAsync: mocks.updateMeeting,
        isPending: false,
    }),
    useUpdateMeetingSettings: () => ({
        mutateAsync: mocks.updateSettings,
        isPending: false,
    }),
}));

vi.mock('../../hooks/useProjectIssues', () => ({
    useProjectIssues: () => ({ issues: [], loading: false, error: null }),
}));

vi.mock('./WorkspaceUserPicker', () => ({
    WorkspaceUserPicker: () => <div data-testid='workspace-user-picker' />,
}));

import { EditMeetingModal } from './EditMeetingModal';

const BASE_MEETING: Meeting = {
    id: 'meeting-1',
    title: 'Lifecycle sync',
    description: 'Original description',
    projectId: 'project-1',
    projectKey: 'SMISKI',
    issueId: '10001',
    issueKey: 'SMISKI-1',
    creatorId: 'host-1',
    creatorName: 'Host',
    hostId: 'host-1',
    hostName: 'Host',
    status: 'SCHEDULED',
    participantCount: 0,
    scheduledAt: '2099-08-09T09:00:00.000Z',
    endTime: '2099-08-09T10:00:00.000Z',
    zoneId: 'UTC',
    settings: {
        admissionPolicy: 'ALLOW_ALL',
        maxParticipants: 50,
        allowScreenShare: true,
        chatEnabled: true,
        allowMicrophone: true,
        allowVideo: true,
    },
};

beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
            matches: false,
            media: query,
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
        }),
    });
    window.ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
    } as unknown as typeof ResizeObserver;
});

describe('EditMeetingModal lifecycle fields', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mocks.meeting = BASE_MEETING;
        mocks.updateMeeting.mockResolvedValue(BASE_MEETING);
        mocks.updateSettings.mockResolvedValue(BASE_MEETING.settings);
        mocks.addInvitees.mockResolvedValue([]);
        mocks.removeInvitees.mockResolvedValue([]);
    });

    afterEach(cleanup);

    function renderModal() {
        const onClose = vi.fn();
        const onSaved = vi.fn();
        render(
            <EditMeetingModal
                isOpen
                meetingId={BASE_MEETING.id}
                onClose={onClose}
                onSaved={onSaved}
            />,
        );
        return { onClose, onSaved };
    }

    it('allows schedule, settings, and invitee changes while SCHEDULED', async () => {
        const user = userEvent.setup();
        renderModal();

        const startDate = await screen.findByLabelText('Start date');
        expect((startDate as HTMLInputElement).disabled).toBe(false);
        expect(screen.getByLabelText('End date')).toBeDefined();
        const endTime = screen.getByLabelText('End time');
        expect(screen.getByText('Settings')).toBeDefined();
        expect(screen.getByText('Invitees')).toBeDefined();
        expect(screen.getByTestId('workspace-user-picker')).toBeDefined();

        await user.clear(endTime);
        await user.type(endTime, '11:00');
        await user.click(screen.getByRole('button', { name: 'Save changes' }));

        await waitFor(() => expect(mocks.updateMeeting).toHaveBeenCalled());
        expect(mocks.updateMeeting).toHaveBeenCalledWith({
            meetingId: BASE_MEETING.id,
            input: expect.objectContaining({
                startTime: '2099-08-09T09:00:00.000Z',
                endTime: '2099-08-09T11:00:00.000Z',
                zoneId: 'UTC',
            }),
        });
    });

    it('edits an instant RUNNING meeting without requiring a time range', async () => {
        mocks.meeting = {
            ...BASE_MEETING,
            status: 'RUNNING',
            scheduledAt: undefined,
            endTime: undefined,
        };
        const user = userEvent.setup();
        const { onClose, onSaved } = renderModal();

        await screen.findByLabelText('Title');
        expect(screen.queryByLabelText('Start date')).toBeNull();
        expect(screen.queryByLabelText('End date')).toBeNull();
        expect(screen.getByText('Settings')).toBeDefined();
        expect(screen.getByText('Invitees')).toBeDefined();
        const title = screen.getByLabelText('Title');
        await user.clear(title);
        await user.type(title, 'Updated instant meeting');
        await user.click(screen.getByRole('button', { name: 'Save changes' }));

        await waitFor(() => expect(mocks.updateMeeting).toHaveBeenCalled());
        const update = mocks.updateMeeting.mock.calls[0]?.[0];
        expect(update).toEqual({
            meetingId: BASE_MEETING.id,
            input: expect.objectContaining({
                title: 'Updated instant meeting',
                detail: mocks.meeting,
            }),
        });
        expect(update?.input).not.toHaveProperty('startTime');
        expect(update?.input).not.toHaveProperty('endTime');
        expect(onSaved).toHaveBeenCalledTimes(1);
        expect(onClose).not.toHaveBeenCalled();
    });

    it.each(['COMPLETED', 'CANCELED'] as const)(
        'limits %s meetings to information fields',
        async (status) => {
            mocks.meeting = { ...BASE_MEETING, status };
            renderModal();

            await screen.findByLabelText('Title');
            expect(screen.queryByLabelText('Start date')).toBeNull();
            expect(screen.queryByLabelText('End date')).toBeNull();
            expect(screen.queryByText('Settings')).toBeNull();
            expect(screen.queryByText('Invitees')).toBeNull();
        },
    );
});
