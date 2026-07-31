package io.github.smiskinext.shared.infrastructure.web;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates framework and unhandled exceptions into RFC 9457 ({@code application/problem+json})
 * responses. Business errors are expected to travel through {@link
 * io.github.smiskinext.shared.domain.Result} and be unwrapped by {@link ResultResponder}; this
 * advice covers only exceptions raised by the servlet/validation stack and unexpected failures.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own status mapping is reused, while
 * {@link ProblemDetailMapper} supplies the localized {@code title}/{@code detail} and the {@code
 * code}/{@code traceId}/{@code errors} extension members. Active only in a SERVLET container.
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = Type.SERVLET)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailMapper problemDetailMapper;

    public GlobalExceptionHandler(ProblemDetailMapper problemDetailMapper) {
        this.problemDetailMapper = problemDetailMapper;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Violation(
                        fe.getField(), resolveViolationCode(fe.getCode()), fe.getDefaultMessage()))
                .toList();
        ProblemDetail body = problemDetailMapper.forErrorCode(
                CommonErrorCode.VALIDATION_ERROR, new Object[0], violations);
        return problemResponse(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return problemResponse(problemDetailMapper.forErrorCode(CommonErrorCode.MALFORMED_REQUEST));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return problemResponse(problemDetailMapper.forErrorCode(CommonErrorCode.MISSING_PARAMETER));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            @NonNull HttpRequestMethodNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return problemResponse(
                problemDetailMapper.forErrorCode(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return problemResponse(
                problemDetailMapper.forErrorCode(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        List<Violation> violations =
                List.of(new Violation(ex.getName(), ViolationCode.INVALID_FORMAT, null));
        ProblemDetail body = problemDetailMapper.forErrorCode(
                CommonErrorCode.VALIDATION_ERROR, new Object[0], violations);
        return problemResponse(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return problemResponse(problemDetailMapper.forErrorCode(CommonErrorCode.NOT_AUTHORIZED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problemResponse(problemDetailMapper.forErrorCode(CommonErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Object> problemResponse(ProblemDetail body) {
        HttpStatus status = HttpStatus.valueOf(body.getStatus());
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(body);
    }

    private ViolationCode resolveViolationCode(String constraintCode) {
        if (constraintCode == null) {
            return ViolationCode.INVALID_VALUE;
        }
        return switch (constraintCode) {
            case "NotBlank", "NotNull", "NotEmpty" -> ViolationCode.REQUIRED;
            case "Email", "Pattern" -> ViolationCode.INVALID_FORMAT;
            case "Size", "Min" -> ViolationCode.TOO_SHORT;
            case "Max" -> ViolationCode.TOO_LONG;
            default -> ViolationCode.INVALID_VALUE;
        };
    }
}
