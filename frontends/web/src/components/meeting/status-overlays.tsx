'use client';

import { Check, Copy } from 'lucide-react';
import { useCallback, useRef, useState } from 'react';

import { ConnectionIndicator } from './connection-indicator.tsx';
import { RecordingIndicator } from './recording-indicator.tsx';

type ConnectionStatus = 'connected' | 'reconnecting' | 'disconnected';

type TimerOverlayProps = {
    formattedDuration: string;
    connectionStatus: ConnectionStatus;
    connectedLabel: string;
    reconnectingLabel: string;
    disconnectedLabel: string;
    displayName?: string;
    shortCode?: string;
};

export function TimerOverlay({
    formattedDuration,
    connectionStatus,
    connectedLabel,
    reconnectingLabel,
    disconnectedLabel,
    displayName,
    shortCode,
}: TimerOverlayProps) {
    const identityLabel = buildIdentityLabel(displayName, shortCode);
    const shortIdentityLabel = buildShortIdentityLabel(displayName, shortCode);
    const [copied, setCopied] = useState(false);
    const resetTimer = useRef<ReturnType<typeof setTimeout>>(null);

    const handleCopy = useCallback(() => {
        if (!identityLabel) return;
        void navigator.clipboard.writeText(identityLabel).then(() => {
            setCopied(true);
            if (resetTimer.current) clearTimeout(resetTimer.current);
            resetTimer.current = setTimeout(() => setCopied(false), 2000);
        });
    }, [identityLabel]);

    return (
        <div className='pointer-events-none absolute bottom-6 left-6 z-20 flex items-center gap-2 rounded-full bg-black/40 px-3 py-1.5 text-white shadow-[0_8px_24px_-8px_rgba(0,0,0,0.45)] backdrop-blur-sm'>
            <ConnectionIndicator
                connectedLabel={connectedLabel}
                disconnectedLabel={disconnectedLabel}
                reconnectingLabel={reconnectingLabel}
                status={connectionStatus}
            />
            {identityLabel && (
                <>
                    <button
                        className='pointer-events-auto flex max-w-[10rem] items-center gap-1 truncate text-sm font-medium transition-opacity hover:opacity-80 lg:max-w-[14rem]'
                        onClick={handleCopy}
                        title={identityLabel}
                        type='button'
                    >
                        <span className='truncate lg:hidden'>
                            {shortIdentityLabel}
                        </span>
                        <span className='hidden truncate lg:inline'>
                            {identityLabel}
                        </span>
                        {copied ? (
                            <Check className='h-3 w-3 shrink-0 text-green-400' />
                        ) : (
                            <Copy className='h-3 w-3 shrink-0 opacity-60' />
                        )}
                    </button>
                    <span aria-hidden='true' className='h-3 w-px bg-white/40' />
                </>
            )}
            <span className='tabular-nums text-sm font-medium'>
                {formattedDuration}
            </span>
        </div>
    );
}

function buildIdentityLabel(
    displayName: string | undefined,
    shortCode: string | undefined,
): string {
    const name = displayName?.trim();
    const code = shortCode?.trim();
    if (name && code) return `${name} | ${code}`;
    return name ?? code ?? '';
}

function buildShortIdentityLabel(
    displayName: string | undefined,
    shortCode: string | undefined,
): string {
    return shortCode?.trim() || displayName?.trim() || '';
}

type RecordingOverlayProps = {
    isVisible: boolean;
};

/**
 * Floating pill at the top-right that surfaces the persistent
 * recording state once the meeting header has been removed. Reuses
 * RecordingIndicator for the pulse + label semantics.
 */
export function RecordingOverlay({ isVisible }: RecordingOverlayProps) {
    if (!isVisible) return null;

    return (
        <div className='pointer-events-none absolute top-6 right-6 z-20 flex items-center rounded-full bg-black/40 px-3 py-1.5 shadow-[0_8px_24px_-8px_rgba(0,0,0,0.45)] backdrop-blur-sm'>
            <RecordingIndicator isVisible={isVisible} />
        </div>
    );
}
