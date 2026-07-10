package io.github.smiskinext.shared.infrastructure.web;

import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.Result;
import java.net.URI;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Unwraps a {@link Result} returned by the application layer into a Spring {@link ResponseEntity}.
 *
 * <p>Success values are returned directly as the response body (no envelope). Failures are mapped
 * to an {@code application/problem+json} {@link ProblemDetail} via {@link ProblemDetailMapper},
 * with the HTTP status derived from the error's {@link io.github.smiskinext.shared.domain.ErrorCategory}.
 *
 * <p>Controllers stay thin and free of {@code try/catch}:
 *
 * <pre>{@code
 * @GetMapping("/meetings/{id}")
 * public ResponseEntity<Object> get(@PathVariable UUID id) {
 *     return responder.ok(getMeeting.byId(id));
 * }
 * }</pre>
 */
@Component
public class ResultResponder {

    private final ProblemDetailMapper problemDetailMapper;

    public ResultResponder(ProblemDetailMapper problemDetailMapper) {
        this.problemDetailMapper = problemDetailMapper;
    }

    /** 200 OK with the success value as body, or the mapped problem response on failure. */
    public <T, E extends DomainError> ResponseEntity<Object> ok(Result<T, E> result) {
        return result.fold(this::okBody, this::problem);
    }

    /**
     * 201 Created with a {@code Location} header derived from the success value, or the mapped
     * problem response on failure.
     */
    public <T, E extends DomainError> ResponseEntity<Object> created(
            Result<T, E> result, Function<T, URI> location) {
        return result.fold(
                value -> ResponseEntity.created(location.apply(value)).body((Object) value),
                this::problem);
    }

    /** 204 No Content on success (value ignored), or the mapped problem response on failure. */
    public <T, E extends DomainError> ResponseEntity<Object> noContent(Result<T, E> result) {
        return result.fold(value -> ResponseEntity.noContent().build(), this::problem);
    }

    private ResponseEntity<Object> okBody(Object value) {
        return ResponseEntity.ok().body(value);
    }

    private ResponseEntity<Object> problem(DomainError error) {
        ProblemDetail body = problemDetailMapper.forDomainError(error);
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
