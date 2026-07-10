package io.github.smiskinext.shared.infrastructure.web;

import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 9457 ({@code application/problem+json}) {@link ProblemDetail} bodies from application
 * error codes.
 *
 * <p>This is the single place that couples domain-level {@link ErrorCode}s to HTTP semantics:
 *
 * <ul>
 *   <li>{@link ErrorCategory} is mapped to an {@link HttpStatus} ({@code status} member);
 *   <li>{@code title} and {@code detail} are resolved from the {@link MessageSource} using the
 *       code-derived keys and the request locale ({@code Accept-Language});
 *   <li>{@code type} is a URI derived from the code under the configured base URI
 *       ({@code app.problem.type-base-uri}), or {@code about:blank} when unset;
 *   <li>extension members {@code code}, {@code traceId}, and (for validation) {@code errors} are
 *       attached for machine consumption.
 * </ul>
 */
@Component
public class ProblemDetailMapper {

    private static final String DEFAULT_TYPE = "about:blank";
    private static final String TRACE_MDC_KEY = "traceId";

    private final MessageSource messageSource;
    private final String typeBaseUri;

    public ProblemDetailMapper(MessageSource messageSource, ProblemProperties properties) {
        this.messageSource = messageSource;
        this.typeBaseUri = normalizeBaseUri(properties.getTypeBaseUri());
    }

    /** Builds a Problem Details body for a domain error returned via {@code Result}. */
    public ProblemDetail forDomainError(DomainError error) {
        return forErrorCode(error.errorCode(), error.messageArgs(), List.of());
    }

    /** Builds a Problem Details body for a bare error code with no interpolation arguments. */
    public ProblemDetail forErrorCode(ErrorCode code) {
        return forErrorCode(code, new Object[0], List.of());
    }

    /**
     * Builds a Problem Details body for an error code, interpolating {@code detailArgs} into the
     * localized {@code detail} and attaching field-level {@code violations} when present.
     */
    public ProblemDetail forErrorCode(
            ErrorCode code, Object[] detailArgs, List<Violation> violations) {
        Locale locale = LocaleContextHolder.getLocale();
        HttpStatus status = toStatus(code.category());

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(typeUri(code));
        problem.setTitle(resolve(code.titleKey(), new Object[0], locale, status.getReasonPhrase()));
        problem.setDetail(resolve(code.detailKey(), detailArgs, locale, problem.getTitle()));
        problem.setProperty("code", code.code());
        problem.setProperty("traceId", currentTraceId());
        if (!violations.isEmpty()) {
            problem.setProperty("errors", violations);
        }
        return problem;
    }

    /** Maps a framework-agnostic {@link ErrorCategory} to its canonical HTTP status. */
    public HttpStatus toStatus(ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String resolve(String key, Object[] args, Locale locale, String fallback) {
        return messageSource.getMessage(key, args, fallback, locale);
    }

    private URI typeUri(ErrorCode code) {
        if (typeBaseUri.isEmpty()) {
            return URI.create(DEFAULT_TYPE);
        }
        return URI.create(typeBaseUri + slug(code));
    }

    private String slug(ErrorCode code) {
        return code.code().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeBaseUri(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base : base + "/";
    }

    private String currentTraceId() {
        String traceId = MDC.get(TRACE_MDC_KEY);
        return traceId != null ? traceId : "";
    }
}
