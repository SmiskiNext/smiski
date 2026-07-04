'use client';

import { useTranslations } from 'next-intl';
import { useState } from 'react';
import {
    Avatar,
    AvatarFallback,
    AvatarImage,
} from '@/components/ui/avatar.tsx';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import type { MeetingManagementMeetingParticipantResponse } from '@/generated/types.gen.ts';

const PARTICIPANT_PREVIEW_COUNT = 5;

type ParticipantListProps = {
    participants?: MeetingManagementMeetingParticipantResponse[] | null;
};

function getInitials(displayName: string): string {
    const words = displayName.trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return '?';
    return words
        .slice(0, 2)
        .map((word) => word.at(0)?.toUpperCase() ?? '')
        .join('');
}

function getRoleLabel(
    role: MeetingManagementMeetingParticipantResponse['role'],
): string | null {
    if (!role) return null;
    return role.charAt(0) + role.slice(1).toLowerCase();
}

export function ParticipantList({ participants }: ParticipantListProps) {
    const t = useTranslations('workspace.history');
    const [expanded, setExpanded] = useState(false);

    if (!participants || participants.length === 0) return null;

    const visibleParticipants = expanded
        ? participants
        : participants.slice(0, PARTICIPANT_PREVIEW_COUNT);
    const hiddenCount = participants.length - visibleParticipants.length;

    return (
        <section className='rounded-3xl border border-border bg-surface p-6 shadow-card'>
            <h2 className='text-xl font-semibold text-text-darkest'>
                {t('participantsSectionTitle', { count: participants.length })}
            </h2>
            <div className='mt-5 space-y-3'>
                {visibleParticipants.map((participant) => {
                    const displayName = participant.displayName ?? '';
                    const roleLabel = getRoleLabel(participant.role);
                    return (
                        <div
                            className='flex items-center justify-between gap-4 rounded-2xl border border-border bg-background px-4 py-3'
                            key={
                                participant.userId
                                ?? participant.meetingId
                                ?? displayName
                            }
                        >
                            <div className='flex min-w-0 items-center gap-3'>
                                <Avatar>
                                    {participant.avatarUrl && (
                                        <AvatarImage
                                            alt={displayName}
                                            src={participant.avatarUrl}
                                        />
                                    )}
                                    <AvatarFallback className='bg-primary-subtle font-semibold text-primary'>
                                        {getInitials(displayName)}
                                    </AvatarFallback>
                                </Avatar>
                                <span className='truncate text-sm font-medium text-text-darkest'>
                                    {displayName}
                                </span>
                            </div>
                            {roleLabel && (
                                <Badge variant='secondary'>{roleLabel}</Badge>
                            )}
                        </div>
                    );
                })}
            </div>
            {hiddenCount > 0 && (
                <Button
                    className='mt-5'
                    onClick={() => setExpanded(true)}
                    type='button'
                    variant='outline'
                >
                    {t('participantsShowMore', { count: hiddenCount })}
                </Button>
            )}
        </section>
    );
}
