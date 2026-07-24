import { Button, Icon } from '../../../components/ui';

export interface DashboardHeaderProps {
    canEditMeeting: boolean;
    onStartMeeting: () => void;
    onScheduleMeeting: () => void;
}

export function DashboardHeader({
    canEditMeeting,
    onStartMeeting,
    onScheduleMeeting,
}: DashboardHeaderProps) {
    return (
        <header className='flex flex-col gap-3 border-b bg-[var(--surface)] px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6'>
            <h1 className='text-lg font-semibold tracking-tight text-[var(--text)]'>
                Meetings
            </h1>
            {canEditMeeting && (
                <div className='flex items-center gap-2'>
                    <Button
                        size='sm'
                        onClick={onScheduleMeeting}
                        leadingIcon={<Icon name='calendar' size={15} />}
                    >
                        Schedule
                    </Button>
                    <Button
                        size='sm'
                        variant='primary'
                        onClick={onStartMeeting}
                        leadingIcon={<Icon name='video' size={15} />}
                    >
                        Start meeting
                    </Button>
                </div>
            )}
        </header>
    );
}
