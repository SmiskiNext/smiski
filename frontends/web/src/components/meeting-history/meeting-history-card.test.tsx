import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { MeetingHistoryCard } from './meeting-history-card.tsx';

const TRANSLATIONS: Record<
    string,
    string | ((values: { count: number }) => string)
> = {
    untitledMeeting: 'Untitled meeting',
    typeScheduled: 'Scheduled',
    typeInstant: 'Instant',
    statusCancelled: 'Cancelled',
    cancelledAriaSuffix: 'cancelled',
    codeCopied: 'Copied',
    copyCode: 'Copy code',
    durationMinutes: ({ count }) => `${count} minute${count === 1 ? '' : 's'}`,
};

vi.mock('next-intl', () => ({
    useTranslations: () => (key: string, values?: { count: number }) => {
        const entry = TRANSLATIONS[key];
        if (typeof entry === 'function') return entry(values ?? { count: 0 });
        return entry ?? key;
    },
    useLocale: () => 'en',
}));

function buildMeeting(
    overrides: Partial<MeetingManagementMeetingResponse> = {},
): MeetingManagementMeetingResponse {
    return {
        id: 'meeting-1',
        hostId: 'host-1',
        shortCode: 'ABC1234567',
        title: 'Team Sync',
        startTime: '2026-06-01T10:00:00Z',
        endTime: '2026-06-01T11:00:00Z',
        type: 'SCHEDULED',
        status: 'ENDED',
        ...overrides,
    };
}

function renderCard(
    overrides: Partial<ComponentProps<typeof MeetingHistoryCard>> = {},
) {
    const props: ComponentProps<typeof MeetingHistoryCard> = {
        meeting: buildMeeting(),
        onClick: vi.fn(),
        ...overrides,
    };

    return {
        ...render(<MeetingHistoryCard {...props} />),
        props,
    };
}

describe('MeetingHistoryCard', () => {
    beforeEach(() => {
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: {
                writeText: vi.fn().mockResolvedValue(undefined),
            },
        });
    });
    it('renders an ENDED meeting without cancelled treatment', () => {
        renderCard({
            meeting: buildMeeting({ status: 'ENDED', title: 'Retrospective' }),
        });

        const card = screen.getByRole('button', { name: 'Retrospective' });
        const title = screen.getByText('Retrospective');

        expect(title).not.toHaveClass('line-through');
        expect(card).not.toHaveClass('opacity-70');
        expect(screen.queryByText('Cancelled')).not.toBeInTheDocument();
    });

    it('renders a CANCELLED meeting with badge, opacity, and cancelled aria label', () => {
        renderCard({
            meeting: buildMeeting({
                status: 'CANCELLED',
                title: 'Budget Review',
            }),
        });

        const card = screen.getByRole('button', {
            name: 'Budget Review — cancelled',
        });
        const title = screen.getByText('Budget Review');

        expect(title).toHaveClass('line-through');
        expect(card).toHaveClass('opacity-70');
        expect(screen.getByText('Cancelled')).toBeInTheDocument();
    });

    it('calls onClick with the meeting when the card is clicked', () => {
        const meeting = buildMeeting();
        const onClick = vi.fn();
        renderCard({ meeting, onClick });

        fireEvent.click(screen.getByRole('button', { name: 'Team Sync' }));

        expect(onClick).toHaveBeenCalledWith(meeting);
    });

    it('calls onClick when Enter and Space are pressed on the focused card', () => {
        const meeting = buildMeeting();
        const onClick = vi.fn();
        renderCard({ meeting, onClick });

        const card = screen.getByRole('button', { name: 'Team Sync' });
        card.focus();

        fireEvent.keyDown(card, { key: 'Enter' });
        fireEvent.keyDown(card, { key: ' ' });

        expect(onClick).toHaveBeenNthCalledWith(1, meeting);
        expect(onClick).toHaveBeenNthCalledWith(2, meeting);
    });

    it('uses the untitled fallback when the meeting title is missing', () => {
        renderCard({ meeting: buildMeeting({ title: '   ' }) });

        expect(
            screen.getByRole('button', { name: 'Untitled meeting' }),
        ).toBeInTheDocument();
    });

    it('copies the meeting short code from the copy button', async () => {
        renderCard();

        fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));

        await waitFor(() => {
            expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
                'ABC1234567',
            );
        });
    });

    it('does not trigger the card click when the copy button is clicked', async () => {
        const onClick = vi.fn();
        renderCard({ onClick });

        fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));

        await waitFor(() => {
            expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
                'ABC1234567',
            );
        });
        expect(onClick).not.toHaveBeenCalled();
    });

    it('hides the copy button when the meeting short code is missing', () => {
        renderCard({ meeting: buildMeeting({ shortCode: undefined }) });

        expect(
            screen.queryByRole('button', { name: 'Copy code' }),
        ).not.toBeInTheDocument();
    });

    it('switches the copy button label after a successful copy', async () => {
        renderCard();

        fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));

        expect(
            await screen.findByRole('button', { name: 'Copied' }),
        ).toBeInTheDocument();
    });
});
