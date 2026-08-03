import { describe, expect, it, vi } from 'vitest';
import { consumeSse, SseConnectionError } from './sseClient';

function streamResponse(chunks: string[]): Response {
    const encoder = new TextEncoder();
    return new Response(
        new ReadableStream({
            start(controller) {
                for (const chunk of chunks)
                    controller.enqueue(encoder.encode(chunk));
                controller.close();
            },
        }),
        { headers: { 'Content-Type': 'text/event-stream' } },
    );
}

describe('consumeSse', () => {
    it('parses chunked named events and ignores heartbeat comments', async () => {
        const onMessage = vi.fn();
        const fetchImplementation = vi
            .fn()
            .mockResolvedValue(
                streamResponse([
                    ': ka\n\nevent: join_request_',
                    'approved\ndata: {"token":"abc",',
                    '"roomName":"room"}\n\n',
                ]),
            );

        await consumeSse('https://api.example/events', {
            fetchImplementation,
            onMessage,
        });

        expect(onMessage).toHaveBeenCalledOnce();
        expect(onMessage).toHaveBeenCalledWith({
            event: 'join_request_approved',
            data: '{"token":"abc","roomName":"room"}',
            id: undefined,
        });
    });

    it('rejects unsuccessful responses before reading the stream', async () => {
        await expect(
            consumeSse('https://api.example/events', {
                fetchImplementation: vi
                    .fn()
                    .mockResolvedValue(new Response(null, { status: 503 })),
                onMessage: vi.fn(),
            }),
        ).rejects.toBeInstanceOf(SseConnectionError);
    });
});
