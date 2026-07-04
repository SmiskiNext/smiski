import { fireEvent, render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { UpcomingMeetingCard } from './upcoming-meeting-card.tsx';

const TRANSLATIONS: Record<string, string> = {
    untitledMeeting: 'Untitled Meeting',
    statusLive: 'Live',
    statusScheduled: 'Scheduled',
    startMeeting: 'Start',
    joinMeeting: 'Join',
    copyCode: 'Copy code',
    codeCopied: 'Copied',
    cancelMeeting: 'Cancel',
};

vi.mock('next-intl', () => ({
    useTranslations: () => (key: string) =>
        key in TRANSLATIONS ? TRANSLATIONS[key] : key,
    useLocale: () => 'en',
}));

vi.mock('next/navigation', () => ({
    useRouter: () => ({ push: vi.fn() }),
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
        status: 'SCHEDULED',
        ...overrides,
    };
}

function renderCard(
    overrides: Partial<ComponentProps<typeof UpcomingMeetingCard>> = {},
) {
    const props: ComponentProps<typeof UpcomingMeetingCard> = {
        meeting: buildMeeting(),
        copiedShortCode: null,
        onCardClick: vi.fn(),
        onCopyLink: vi.fn().mockResolvedValue(undefined),
        onOpenSettings: vi.fn(),
        onCancel: vi.fn(),
        ...overrides,
    };
    return render(<UpcomingMeetingCard {...props} />);
}

describe('UpcomingMeetingCard actions', () => {
    it('renders Cancel only for SCHEDULED meetings', () => {
        const { rerender } = renderCard({
            meeting: buildMeeting({ status: 'SCHEDULED' }),
        });

        expect(
            screen.getByRole('button', { name: 'Cancel' }),
        ).toBeInTheDocument();

        rerender(
            <UpcomingMeetingCard
                copiedShortCode={null}
                meeting={buildMeeting({ status: 'LIVE' })}
                onCancel={vi.fn()}
                onCardClick={vi.fn()}
                onCopyLink={vi.fn().mockResolvedValue(undefined)}
                onOpenSettings={vi.fn()}
            />,
        );

        expect(
            screen.queryByRole('button', { name: 'Cancel' }),
        ).not.toBeInTheDocument();
    });

    it('clicks the inline Cancel button without opening the card', () => {
        const meeting = buildMeeting({ status: 'SCHEDULED' });
        const onCancel = vi.fn();
        const onCardClick = vi.fn();
        renderCard({ meeting, onCancel, onCardClick });

        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

        expect(onCancel).toHaveBeenCalledWith(meeting);
        expect(onCardClick).not.toHaveBeenCalled();
    });

    it('clicks the inline Copy button with the short code without opening the card', () => {
        const meeting = buildMeeting();
        const onCopyLink = vi.fn().mockResolvedValue(undefined);
        const onCardClick = vi.fn();
        renderCard({ meeting, onCardClick, onCopyLink });

        fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));

        expect(onCopyLink).toHaveBeenCalledWith('ABC1234567');
        expect(onCardClick).not.toHaveBeenCalled();
    });

    it('labels the Copy button as Copied when the meeting short code matches', () => {
        renderCard({ copiedShortCode: 'ABC1234567' });

        expect(
            screen.getByRole('button', { name: 'Copied' }),
        ).toBeInTheDocument();
    });
});
