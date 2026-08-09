// @vitest-environment jsdom
/**
 * Every surface that opens a Forge platform modal must copy the published
 * numeric Jira identifiers into the modal context, because the modal iframe's
 * Forge context carries that payload rather than the originating issue. An
 * opener that drops them leaves its modal issuing backend requests without
 * `x-issue-id` / `x-project-id`, which makes the gateway skip its permission
 * check and answer 403 from every guarded endpoint — a failure reproducible
 * only on a real Jira site.
 *
 * These tests cover identifier propagation alone; each opener's own modal
 * behavior is covered elsewhere.
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { modalOpened, findRunningMeetingHostedByUser, useHostConflict } =
    vi.hoisted(() => ({
        modalOpened: vi.fn(),
        findRunningMeetingHostedByUser: vi.fn(),
        useHostConflict: vi.fn(),
    }));

vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: vi.fn(),
    requestJira: vi.fn(),
    Modal: class {
        constructor(options: unknown) {
            modalOpened(options);
        }
        open() {
            return Promise.resolve();
        }
    },
}));

const cancelMutate = vi.fn();
const endMutate = vi.fn();
const deleteMutate = vi.fn();

vi.mock('./useMeetingMutations', () => ({
    useCancelMeeting: () => ({
        mutate: cancelMutate,
        isPending: false,
        error: null,
        reset: vi.fn(),
    }),
    useEndMeeting: () => ({
        mutate: endMutate,
        isPending: false,
        error: null,
        reset: vi.fn(),
    }),
    useDeleteMeeting: () => ({
        mutate: deleteMutate,
        isPending: false,
        error: null,
        reset: vi.fn(),
    }),
}));

vi.mock('../api/meetings', () => ({ findRunningMeetingHostedByUser }));

vi.mock('./useHostConflict', () => ({ useHostConflict }));

vi.mock('../context/CurrentUserContext', () => ({
    useCurrentUser: () => ({ accountId: 'acc-host' }),
}));

vi.mock('@smiskinext/sdks-jira', () => ({
    findUsers: vi.fn(),
    getAllUsers: vi.fn(),
    getAllPermissions: vi.fn(),
    getMyPermissions: vi.fn(),
}));

import { clearBackendContext, setBackendContext } from '../api/backendContext';
import type { Meeting } from '../domain';
import { StartInstantMeetingButton } from '../features/issue-panel/StartInstantMeetingButton';
import { useConfirmMeetingAction } from './useConfirmMeetingAction';
import { useHostConflictGuard } from './useHostConflictGuard';
import { useIssuePanelInstantModal } from './useIssuePanelInstantModal';
import { useIssuePanelMeetingDetailModal } from './useIssuePanelMeetingDetailModal';
import { useIssuePanelScheduleModal } from './useIssuePanelScheduleModal';

const ISSUE_ID = '10001';
const PROJECT_ID = '10002';

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

function QueryWrapper({ children }: { children: ReactNode }) {
    const client = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    });
    return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
}

/** The context object the opener handed to the Forge `Modal` constructor. */
function openedContext(): Record<string, unknown> {
    expect(modalOpened).toHaveBeenCalledTimes(1);
    return (
        modalOpened.mock.calls[0][0] as { context: Record<string, unknown> }
    ).context;
}

function expectIdentifiersCarried() {
    expect(openedContext()).toMatchObject({
        issueId: ISSUE_ID,
        projectId: PROJECT_ID,
    });
}

describe('platform-modal openers carry the published Jira identifiers', () => {
    beforeEach(() => {
        modalOpened.mockReset();
        cancelMutate.mockReset();
        endMutate.mockReset();
        findRunningMeetingHostedByUser.mockReset();
        useHostConflict.mockReset();
        clearBackendContext();
        setBackendContext({ issueId: ISSUE_ID, projectId: PROJECT_ID });
    });

    it('useIssuePanelScheduleModal', () => {
        const { result } = renderHook(() => useIssuePanelScheduleModal(), {
            wrapper: QueryWrapper,
        });

        result.current.open({
            issueId: ISSUE_ID,
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });

        expectIdentifiersCarried();
    });

    it('useIssuePanelInstantModal', () => {
        const { result } = renderHook(() => useIssuePanelInstantModal(), {
            wrapper: QueryWrapper,
        });

        result.current.open({
            issueId: ISSUE_ID,
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });

        expectIdentifiersCarried();
    });

    it('useIssuePanelMeetingDetailModal', () => {
        const { result } = renderHook(() => useIssuePanelMeetingDetailModal());

        result.current.open(MEETING);

        expectIdentifiersCarried();
    });

    it('useConfirmMeetingAction in its platform-modal presentation', () => {
        const { result } = renderHook(() =>
            useConfirmMeetingAction(vi.fn(), 'platform-modal'),
        );

        result.current.request('CANCEL', MEETING);

        expectIdentifiersCarried();
    });

    it('useHostConflictGuard in its platform-modal presentation', async () => {
        findRunningMeetingHostedByUser.mockResolvedValue(MEETING);
        const { result } = renderHook(() =>
            useHostConflictGuard('platform-modal'),
        );

        await result.current.guard('SMISKI-202', vi.fn());

        expectIdentifiersCarried();
    });

    it('StartInstantMeetingButton', async () => {
        useHostConflict.mockReturnValue({
            conflictingMeeting: MEETING,
            loading: false,
        });
        render(
            <StartInstantMeetingButton
                issueId={ISSUE_ID}
                issueKey='SMISKI-202'
                projectKey='SMISKI'
                onOpenInstantModal={vi.fn()}
            />,
        );

        await userEvent.click(
            screen.getByRole('button', { name: /start instant/i }),
        );

        expectIdentifiersCarried();
    });
});

describe('explicit modal issue context wins over stale published context', () => {
    beforeEach(() => {
        modalOpened.mockReset();
        clearBackendContext();
        setBackendContext({ issueId: ISSUE_ID, projectId: PROJECT_ID });
    });

    it('keeps the issue id paired with the explicitly selected issue', () => {
        const { result } = renderHook(() => useIssuePanelScheduleModal(), {
            wrapper: QueryWrapper,
        });

        result.current.open({
            issueId: '20002',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });

        expect(openedContext()).toMatchObject({
            issueId: '20002',
            projectId: PROJECT_ID,
        });
    });
});
