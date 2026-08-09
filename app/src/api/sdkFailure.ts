/**
 * Renders an SDK failure into a single readable line for the Forge app log.
 *
 * A `@smiskinext/smiski-ts` operation configured with `throwOnError: false`
 * reports every failure as a result whose `error` is present, covering two
 * shapes: an RFC 9457 Problem Details body parsed from a non-success response,
 * and the raw value thrown by the transport when the remote was unreachable.
 * The `response` is absent in the second case, so the status is only included
 * when one was actually received.
 */
import type { TenantProblemDetail } from '@smiskinext/smiski-ts';

const UNREACHABLE = 'the meeting backend could not be reached';

/**
 * Narrow an SDK error to Problem Details. The SDK types the field as the
 * operation's error schema, but the transport-failure path puts the thrown
 * value there instead, so the shape is checked rather than trusted.
 */
function asProblemDetail(error: unknown): TenantProblemDetail | undefined {
    if (typeof error !== 'object' || error === null) return undefined;
    if (error instanceof Error) return undefined;
    return error as TenantProblemDetail;
}

/**
 * Read a human-readable reason from a value that is not Problem Details: the
 * message of a thrown `Error`, or a bare string body.
 */
function readRawReason(error: unknown): string | undefined {
    if (error instanceof Error && error.message !== '') return error.message;
    if (typeof error === 'string' && error !== '') return error;
    return undefined;
}

export function describeSdkFailure(
    error: unknown,
    response: Response | undefined,
): string {
    const parts: string[] = [];

    if (response !== undefined) {
        parts.push(`status ${response.status}`);
    }

    const problem = asProblemDetail(error);
    if (problem?.code !== undefined) parts.push(`code ${problem.code}`);
    if (problem?.traceId !== undefined)
        parts.push(`traceId ${problem.traceId}`);

    const reason = problem?.detail ?? problem?.title ?? readRawReason(error);
    if (reason !== undefined) parts.push(reason);

    return parts.length === 0 ? UNREACHABLE : parts.join(', ');
}
