'use client';

import { CalendarPlus, Video } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';
import { toast } from 'sonner';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu.tsx';
import { MEETING_SETTINGS_DEFAULTS } from '@/lib/schemas/meeting.ts';
import { useCreateMeeting } from './use-create-meeting.ts';

type NewMeetingDropdownProps = {
    children: React.ReactNode;
};

export function NewMeetingDropdown({ children }: NewMeetingDropdownProps) {
    const t = useTranslations('createMeeting');
    const locale = useLocale();
    const router = useRouter();
    const { state, create, reset } = useCreateMeeting();
    const loadingToastIdRef = useRef<string | number | null>(null);

    const isCreating = state.phase === 'CREATING';

    useEffect(() => {
        if (state.phase === 'CREATING') {
            loadingToastIdRef.current = toast.loading(t('creating'));
            return;
        }

        if (loadingToastIdRef.current !== null) {
            toast.dismiss(loadingToastIdRef.current);
            loadingToastIdRef.current = null;
        }

        if (state.phase === 'READY') {
            router.push(
                `/${locale}/workspace/green-room?code=${state.shortCode}`,
            );
            reset();
            return;
        }

        if (state.phase === 'ERROR') {
            toast.error(state.message);
            reset();
        }
    }, [state, t, router, locale, reset]);

    function handleStartInstantMeeting() {
        if (isCreating) {
            return;
        }
        void create({
            title: '',
            settings: MEETING_SETTINGS_DEFAULTS,
        });
    }

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>{children}</DropdownMenuTrigger>
            <DropdownMenuContent align='end' className='min-w-[220px]'>
                <DropdownMenuItem
                    className='gap-3 py-3'
                    disabled={isCreating}
                    onSelect={handleStartInstantMeeting}
                >
                    <Video className='h-4 w-4 text-primary' />
                    <div>
                        <p className='font-medium'>
                            {t('instantMeetingAction')}
                        </p>
                        <p className='text-xs text-text-subtle'>
                            {t('instantMeetingActionDescription')}
                        </p>
                    </div>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className='gap-3 py-3'>
                    <Link href={`/${locale}/workspace/schedule`}>
                        <CalendarPlus className='h-4 w-4 text-primary' />
                        <div>
                            <p className='font-medium'>
                                {t('scheduleMeetingAction')}
                            </p>
                            <p className='text-xs text-text-subtle'>
                                {t('scheduleMeetingActionDescription')}
                            </p>
                        </div>
                    </Link>
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
