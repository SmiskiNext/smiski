'use client';

import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import { cn } from '@/lib/utils.ts';

type ConnectionStatus = 'connected' | 'reconnecting' | 'disconnected';

type ConnectionIndicatorProps = {
    status: ConnectionStatus;
    connectedLabel: string;
    reconnectingLabel: string;
    disconnectedLabel: string;
};

const STATUS_CONFIG: Record<
    ConnectionStatus,
    { dotClass: string; pulseClass: string }
> = {
    connected: {
        dotClass: 'bg-green-500',
        pulseClass: 'bg-green-500/30',
    },
    reconnecting: {
        dotClass: 'bg-yellow-500',
        pulseClass: 'bg-yellow-500/30',
    },
    disconnected: {
        dotClass: 'bg-red-500',
        pulseClass: 'bg-red-500/30',
    },
};

function statusLabel(
    status: ConnectionStatus,
    labels: Omit<ConnectionIndicatorProps, 'status'>,
): string {
    if (status === 'connected') return labels.connectedLabel;
    if (status === 'reconnecting') return labels.reconnectingLabel;
    return labels.disconnectedLabel;
}

/**
 * Accessible connection state indicator shown in the meeting header.
 * Uses a colored dot with tooltip and aria-live for screen readers.
 */
export function ConnectionIndicator({
    status,
    connectedLabel,
    reconnectingLabel,
    disconnectedLabel,
}: ConnectionIndicatorProps) {
    const config = STATUS_CONFIG[status];
    const label = statusLabel(status, {
        connectedLabel,
        reconnectingLabel,
        disconnectedLabel,
    });
    const isAnimated = status === 'reconnecting';

    return (
        <TooltipProvider>
            <Tooltip>
                <TooltipTrigger asChild>
                    <div
                        aria-label={label}
                        aria-live='polite'
                        className='relative flex h-5 w-5 items-center justify-center'
                        role='status'
                    >
                        {isAnimated && (
                            <span
                                className={cn(
                                    'absolute inline-flex h-full w-full animate-ping rounded-full opacity-75',
                                    config.pulseClass,
                                )}
                            />
                        )}
                        <span
                            className={cn(
                                'relative inline-flex h-3 w-3 rounded-full',
                                config.dotClass,
                            )}
                        />
                    </div>
                </TooltipTrigger>
                <TooltipContent side='bottom'>
                    <p>{label}</p>
                </TooltipContent>
            </Tooltip>
        </TooltipProvider>
    );
}
