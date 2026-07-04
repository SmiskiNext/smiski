'use client';

import { useRoomContext } from '@livekit/components-react';
import { RoomEvent } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getMessages, sendMessage } from '@/generated/sdk.gen.ts';
import type { ChatMessage } from '@/types/chat.ts';

type UseMeetingChatResult = {
    messages: ChatMessage[];
    loading: boolean;
    error: boolean;
    unreadCount: number;
    loadHistory: () => void;
    send: (content: string, senderName: string) => Promise<void>;
    sendError: boolean;
};

function mergeMessages(
    existing: ChatMessage[],
    incoming: ChatMessage[],
): ChatMessage[] {
    const map = new Map<string, ChatMessage>(existing.map((m) => [m.id, m]));
    for (const msg of incoming) {
        map.set(msg.id, msg);
    }
    return Array.from(map.values()).sort((a, b) => a.seqNum - b.seqNum);
}

function parseDataChannelPayload(payload: Uint8Array): ChatMessage | null {
    try {
        const text = new TextDecoder('utf-8').decode(payload);
        const raw = JSON.parse(text) as Record<string, unknown>;
        if (
            typeof raw.id !== 'string'
            || typeof raw.seqNum !== 'number'
            || typeof raw.senderId !== 'string'
            || typeof raw.content !== 'string'
        ) {
            return null;
        }
        return {
            id: raw.id,
            seqNum: raw.seqNum,
            roomId: typeof raw.roomId === 'string' ? raw.roomId : '',
            senderId: raw.senderId,
            senderName:
                typeof raw.senderName === 'string' ? raw.senderName : '',
            content: raw.content,
            type:
                raw.type === 'SYSTEM' || raw.type === 'TEXT'
                    ? raw.type
                    : 'TEXT',
            createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
        };
    } catch {
        return null;
    }
}

/**
 * Manages in-meeting chat state: history loading, message sending, and
 * real-time delivery via LiveKit data channel. De-duplicates by id and
 * maintains ascending seqNum order. Tracks unread count when the chat
 * panel is not visible.
 */
export function useMeetingChat(
    meetingId: string,
    userId: string,
    isChatVisible: boolean,
): UseMeetingChatResult {
    const room = useRoomContext();

    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [sendError, setSendError] = useState(false);

    const historyLoadedRef = useRef(false);
    const isMountedRef = useRef(true);

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        if (isChatVisible) {
            setUnreadCount(0);
        }
    }, [isChatVisible]);

    const loadHistory = useCallback(() => {
        if (historyLoadedRef.current) return;
        historyLoadedRef.current = true;

        setLoading(true);
        setError(false);

        getMessages({ path: { roomId: meetingId } })
            .then(({ data }) => {
                if (!isMountedRef.current) return;
                const raw = data?.content ?? [];
                const fetched: ChatMessage[] = raw.reduce<ChatMessage[]>(
                    (acc, m) => {
                        if (
                            m.id == null
                            || m.seqNum == null
                            || m.senderId == null
                            || m.content == null
                        ) {
                            return acc;
                        }
                        acc.push({
                            id: m.id,
                            seqNum: m.seqNum,
                            roomId: m.roomId ?? '',
                            senderId: m.senderId,
                            senderName: m.senderName ?? '',
                            content: m.content,
                            type:
                                m.type === 'SYSTEM' || m.type === 'TEXT'
                                    ? m.type
                                    : 'TEXT',
                            createdAt: m.createdAt ?? '',
                        });
                        return acc;
                    },
                    [],
                );
                setMessages((prev) => mergeMessages(prev, fetched));
                setLoading(false);
            })
            .catch(() => {
                if (!isMountedRef.current) return;
                historyLoadedRef.current = false;
                setLoading(false);
                setError(true);
            });
    }, [meetingId]);

    const send = useCallback(
        async (content: string, senderName: string) => {
            setSendError(false);
            try {
                const { data } = await sendMessage({
                    path: { roomId: meetingId },
                    body: { content, senderName },
                    throwOnError: true,
                });
                if (data && data.id != null && data.seqNum != null) {
                    const sent: ChatMessage = {
                        id: data.id,
                        seqNum: data.seqNum,
                        roomId: data.roomId ?? meetingId,
                        senderId: data.senderId ?? userId,
                        senderName: data.senderName ?? senderName,
                        content: data.content ?? content,
                        type:
                            data.type === 'SYSTEM' || data.type === 'TEXT'
                                ? data.type
                                : 'TEXT',
                        createdAt: data.createdAt ?? new Date().toISOString(),
                    };
                    if (isMountedRef.current) {
                        setMessages((prev) => mergeMessages(prev, [sent]));
                    }
                }
            } catch {
                if (isMountedRef.current) {
                    setSendError(true);
                }
            }
        },
        [meetingId, userId],
    );

    useEffect(() => {
        function handleDataReceived(
            payload: Uint8Array,
            _participant: unknown,
            _kind: unknown,
            _topic: unknown,
        ) {
            const msg = parseDataChannelPayload(payload);
            if (!msg) {
                console.debug('[useMeetingChat] dropped malformed data packet');
                return;
            }
            if (isMountedRef.current) {
                setMessages((prev) => mergeMessages(prev, [msg]));
                if (!isChatVisible) {
                    setUnreadCount((n) => n + 1);
                }
            }
        }

        room.on(RoomEvent.DataReceived, handleDataReceived);
        return () => {
            room.off(RoomEvent.DataReceived, handleDataReceived);
        };
    }, [room, isChatVisible]);

    return {
        messages,
        loading,
        error,
        unreadCount,
        loadHistory,
        send,
        sendError,
    };
}
