package io.github.phunguy65.zms.shared.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSend-compliant response envelope.
 *
 * <ul>
 *   <li>{@code success} – 2xx: operation succeeded, {@code data} contains the result.
 *   <li>{@code fail} – 4xx: business/validation error, {@code data} contains error details.
 *   <li>{@code error} – 5xx: technical/server error, {@code message} describes the problem.
 * </ul>
 *
 * @see <a href="https://github.com/omniti-labs/jsend">JSend specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsendResponse<T>(String status, T data, String message) {

    /** 2xx – successful operation with data payload. */
    public static <T> JsendResponse<T> success(T data) {
        return new JsendResponse<>("success", data, null);
    }

    /** 2xx – successful operation with no data (e.g. DELETE). */
    public static JsendResponse<Void> success() {
        return new JsendResponse<>("success", null, null);
    }

    /** 4xx – business/validation failure; {@code data} holds field-level or message detail. */
    public static <T> JsendResponse<T> fail(T data) {
        return new JsendResponse<>("fail", data, null);
    }

    /** 5xx – unexpected technical error; only a human-readable message is exposed. */
    public static JsendResponse<Void> error(String message) {
        return new JsendResponse<>("error", null, message);
    }
}
