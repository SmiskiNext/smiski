'use client';

import { Loader2, MessageSquare, Send } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button.tsx';
import type { ChatMessage } from '@/types/chat.ts';

type MeetingChatProps = {
    messages: ChatMessage[];
    loading: boolean;
    error: boolean;
    sendError: boolean;
    userId: string;
    onRetry: () => void;
    onSend: (content: string) => void;
};

type SystemMessageRowProps = {
    message: ChatMessage;
};

type OutgoingMessageRowProps = {
    message: ChatMessage;
};

type IncomingMessageRowProps = {
    message: ChatMessage;
};

function SystemMessageRow({ message }: SystemMessageRowProps) {
    return (
        <div className='flex justify-center py-1'>
            <span className='text-center text-[0.82rem] italic text-text-muted'>
                {message.content}
            </span>
        </div>
    );
}

function OutgoingMessageRow({ message }: OutgoingMessageRowProps) {
    return (
        <div className='flex flex-col items-end gap-1'>
            <div className='max-w-[80%] rounded-[1rem] bg-primary px-4 py-3 text-[0.97rem] leading-7 text-white'>
                {message.content}
            </div>
        </div>
    );
}

function IncomingMessageRow({ message }: IncomingMessageRowProps) {
    return (
        <div className='flex flex-col items-start gap-1'>
            <span className='px-1 text-[0.82rem] font-semibold text-text-secondary'>
                {message.senderName}
            </span>
            <div className='max-w-[80%] rounded-[1rem] bg-surface-input px-4 py-3 text-[0.97rem] leading-7 text-text-primary'>
                {message.content}
            </div>
        </div>
    );
}

/**
 * Chat panel rendered inside the meeting sidebar. Displays three distinct
 * message styles (outgoing, incoming, system), plus loading, empty, and error
 * states. Auto-scrolls to the bottom whenever the message list grows.
 * Shows a brief inline error banner when a send operation fails.
 */
export function MeetingChat({
    messages,
    loading,
    error,
    sendError,
    userId,
    onRetry,
    onSend,
}: MeetingChatProps) {
    const t = useTranslations('meetingRoom');
    const [inputValue, setInputValue] = useState('');
    const scrollRef = useRef<HTMLDivElement>(null);

    // biome-ignore lint/correctness/useExhaustiveDependencies: scroll fires on message list growth
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages.length]);

    function handleSend() {
        const text = inputValue.trim();
        if (!text) return;
        onSend(text);
        setInputValue('');
    }

    return (
        <>
            <div
                className='flex-1 space-y-3 overflow-y-auto px-5 py-5'
                ref={scrollRef}
            >
                {loading && (
                    <div className='flex items-center justify-center gap-2 py-8'>
                        <Loader2 className='h-4 w-4 animate-spin text-text-muted' />
                        <span className='text-[0.9rem] text-text-muted'>
                            {t('chatLoading')}
                        </span>
                    </div>
                )}

                {error && !loading && (
                    <div className='flex flex-col items-center gap-3 py-8'>
                        <p className='text-[0.9rem] text-error'>
                            {t('chatError')}
                        </p>
                        <Button
                            onClick={onRetry}
                            size='sm'
                            type='button'
                            variant='secondary'
                        >
                            {t('chatRetry')}
                        </Button>
                    </div>
                )}

                {!loading && !error && messages.length === 0 && (
                    <div className='flex flex-col items-center gap-3 py-8'>
                        <MessageSquare className='h-10 w-10 text-text-muted opacity-40' />
                        <p className='text-[0.9rem] text-text-muted'>
                            {t('chatEmpty')}
                        </p>
                    </div>
                )}

                {!loading
                    && messages.map((msg) =>
                        msg.type === 'SYSTEM' ? (
                            <SystemMessageRow key={msg.id} message={msg} />
                        ) : msg.senderId === userId ? (
                            <OutgoingMessageRow key={msg.id} message={msg} />
                        ) : (
                            <IncomingMessageRow key={msg.id} message={msg} />
                        ),
                    )}
            </div>

            {sendError && (
                <div
                    aria-live='polite'
                    className='shrink-0 px-5 py-2 text-center text-sm text-error'
                    role='alert'
                >
                    {t('chatSendError')}
                </div>
            )}

            <div className='shrink-0 border-t border-border p-4'>
                <div className='flex items-center gap-3 rounded-full bg-surface-input px-5 py-3 ring-1 ring-transparent transition focus-within:ring-2 focus-within:ring-primary'>
                    <input
                        className='flex-1 bg-transparent text-[0.97rem] text-text-dark outline-none placeholder:text-text-message-time'
                        onChange={(e) => setInputValue(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                        placeholder={t('messagePlaceholder')}
                        type='text'
                        value={inputValue}
                    />
                    <button
                        className='flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-white shadow-sm transition-all hover:bg-primary-hover disabled:opacity-40'
                        disabled={!inputValue.trim()}
                        onClick={handleSend}
                        type='button'
                    >
                        <Send className='h-5 w-5' />
                    </button>
                </div>
            </div>
        </>
    );
}
