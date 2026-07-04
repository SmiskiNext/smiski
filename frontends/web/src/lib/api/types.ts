/**
 * Typed exception for JSend {@code "fail"} responses (HTTP 4xx).
 *
 * Contains a machine-readable {@link code}, a human-readable {@link message},
 * and an optional list of field-level {@link errors}.
 */
export class ApiFailError extends Error {
    readonly code: string;
    readonly errors: Violation[];

    constructor(code: string, message: string, errors: Violation[] = []) {
        super(message);
        this.name = 'ApiFailError';
        this.code = code;
        this.errors = errors;
    }
}

/**
 * Typed exception for JSend {@code "error"} responses (HTTP 5xx).
 *
 * Contains only a human-readable message; the server does not expose
 * machine-readable error codes for 5xx failures.
 */
export class ApiError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'ApiError';
    }
}

/** Single field-level validation failure. */
export interface Violation {
    field: string;
    message: string;
    code: string;
}

/**
 * Hook for client-side i18n of error messages.
 *
 * Returns a translated message for the given error code,
 * or {@link defaultMessage} as-is if no translation is available.
 */
export type ErrorTranslator = (code: string, defaultMessage: string) => string;

/** Pass-through translator that always returns the original message. */
export const defaultTranslator: ErrorTranslator = (
    _code: string,
    defaultMessage: string,
) => defaultMessage;
