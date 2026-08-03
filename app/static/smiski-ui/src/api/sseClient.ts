export interface SseMessage {
    event: string;
    data: string;
    id?: string;
}

export interface ConsumeSseOptions {
    signal?: AbortSignal;
    headers?: HeadersInit;
    fetchImplementation?: typeof fetch;
    onMessage: (message: SseMessage) => boolean | undefined;
}

export class SseConnectionError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'SseConnectionError';
    }
}

function parseFrame(frame: string): SseMessage | null {
    let event = 'message';
    let id: string | undefined;
    const data: string[] = [];

    for (const line of frame.split(/\r?\n/)) {
        if (line === '' || line.startsWith(':')) continue;
        const separator = line.indexOf(':');
        const field = separator === -1 ? line : line.slice(0, separator);
        let value = separator === -1 ? '' : line.slice(separator + 1);
        if (value.startsWith(' ')) value = value.slice(1);

        if (field === 'event') event = value;
        if (field === 'data') data.push(value);
        if (field === 'id') id = value;
    }

    if (data.length === 0) return null;
    return { event, data: data.join('\n'), id };
}

function takeFrame(buffer: string): { frame: string; rest: string } | null {
    const boundary = /\r?\n\r?\n/.exec(buffer);
    if (!boundary || boundary.index === undefined) return null;
    return {
        frame: buffer.slice(0, boundary.index),
        rest: buffer.slice(boundary.index + boundary[0].length),
    };
}

/** Reads one browser-native text/event-stream response until it closes or is stopped. */
export async function consumeSse(
    url: string,
    options: ConsumeSseOptions,
): Promise<void> {
    const fetchImplementation = options.fetchImplementation ?? fetch;
    const response = await fetchImplementation(url, {
        method: 'GET',
        headers: {
            Accept: 'text/event-stream',
            ...options.headers,
        },
        signal: options.signal,
    });

    if (!response.ok) {
        throw new SseConnectionError(
            `SSE request failed with status ${response.status}.`,
        );
    }
    if (!response.body) {
        throw new SseConnectionError('SSE response has no readable body.');
    }

    const contentType = response.headers.get('content-type');
    if (contentType && !contentType.includes('text/event-stream')) {
        throw new SseConnectionError(
            `Expected text/event-stream but received ${contentType}.`,
        );
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
        while (!options.signal?.aborted) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });

            let extracted = takeFrame(buffer);
            while (extracted) {
                buffer = extracted.rest;
                const message = parseFrame(extracted.frame);
                if (message && options.onMessage(message) === false) {
                    await reader.cancel();
                    return;
                }
                extracted = takeFrame(buffer);
            }
        }
    } finally {
        reader.releaseLock();
    }
}
