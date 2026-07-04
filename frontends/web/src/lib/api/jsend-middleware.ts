import {
    ApiError,
    ApiFailError,
    defaultTranslator,
    type ErrorTranslator,
    type Violation,
} from './types.ts';

/**
 * JSend envelope shape as returned by the server.
 *
 * - `"success"`: {@link data} contains the payload, {@link message} is absent.
 * - `"fail"`:    {@link data} contains a FailData object.
 * - `"error"`:   {@link data} is absent, {@link message} describes the problem.
 */
interface JsendEnvelope {
    status: 'success' | 'fail' | 'error';
    data?: unknown;
    message?: string;
}

interface FailData {
    code?: string;
    message?: string;
    errors?: Violation[];
}

function isJsendEnvelope(value: unknown): value is JsendEnvelope {
    return (
        typeof value === 'object'
        && value !== null
        && 'status' in value
        && typeof (value as JsendEnvelope).status === 'string'
    );
}

/**
 * Creates a response interceptor that unwraps JSend envelopes.
 *
 * The hey-api client's response interceptors receive the raw `Response` object
 * **before** the client parses the body. This interceptor clones the response,
 * reads the body as JSON, and replaces the body with the unwrapped `data` field
 * on success, or throws typed exceptions on fail/error.
 *
 * - On `"success"`: returns a new response with the body replaced by the `data` value.
 * - On `"fail"`:    throws {@link ApiFailError} with code, message, and violations.
 * - On `"error"`:   throws {@link ApiError} with the server error message.
 *
 * @param translator — optional i18n hook to translate error codes to locale-specific messages
 */
export function createJsendMiddleware(
    translator: ErrorTranslator = defaultTranslator,
) {
    return async (response: Response): Promise<Response> => {
        const contentType = response.headers.get('content-type');
        if (!contentType?.includes('json')) {
            return response;
        }

        const cloned = response.clone();
        let body: unknown;
        try {
            body = await cloned.json();
        } catch {
            return response;
        }

        if (!isJsendEnvelope(body)) {
            return response;
        }

        switch (body.status) {
            case 'success': {
                const unwrapped = JSON.stringify(body.data ?? null);
                const headers = new Headers(response.headers);
                headers.delete('content-length');
                headers.delete('transfer-encoding');
                return new Response(unwrapped, {
                    status: response.status,
                    statusText: response.statusText,
                    headers,
                });
            }

            case 'fail': {
                const failData = (body.data ?? {}) as FailData;
                const code = failData.code ?? '';
                const message = failData.message ?? 'Request failed';
                const rawErrors = failData.errors ?? [];
                const translatedMessage = translator(code, message);
                const errors = rawErrors.map((violation) => ({
                    ...violation,
                    message: violation.code
                        ? translator(violation.code, violation.message)
                        : violation.message,
                }));
                throw new ApiFailError(code, translatedMessage, errors);
            }

            case 'error': {
                const message =
                    body.message ?? 'An unexpected server error occurred';
                throw new ApiError(message);
            }

            default:
                return response;
        }
    };
}
