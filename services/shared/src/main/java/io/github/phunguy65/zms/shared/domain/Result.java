package io.github.phunguy65.zms.shared.domain;

import java.util.function.Function;

/**
 * A discriminated union representing either a successful value ({@link Success}) or a failure
 * value ({@link Failure}).
 *
 * <p>Domain methods return {@code Result} instead of throwing exceptions for business-rule
 * violations. Only truly exceptional / technical situations (I/O errors, infrastructure failures)
 * should still propagate as Java exceptions.
 *
 * <pre>{@code
 * // Domain method
 * public Result<Void, BookingError> confirm() { ... }
 *
 * // Caller
 * return switch (booking.confirm()) {
 *     case Result.Success<Void, BookingError> s -> handleSuccess();
 *     case Result.Failure<Void, BookingError> f -> handleError(f.error());
 * };
 * }</pre>
 *
 * @param <T> the success value type
 * @param <E> the failure/error value type
 */
public sealed interface Result<T, E> {

    record Success<T, E>(T value) implements Result<T, E> {}

    record Failure<T, E>(E error) implements Result<T, E> {}

    // ------------------------------------------------------------------
    // Factory helpers
    // ------------------------------------------------------------------

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    /** Convenience overload for {@code Result<Void, E>}. */
    static <E> Result<Void, E> success() {
        return new Success<>(null);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }

    // ------------------------------------------------------------------
    // Transform helpers
    // ------------------------------------------------------------------

    /**
     * Maps the success value; leaves failures untouched.
     */
    default <U> Result<U, E> map(Function<T, U> mapper) {
        return switch (this) {
            case Success<T, E> s -> new Success<>(mapper.apply(s.value()));
            case Failure<T, E> f -> new Failure<>(f.error());
        };
    }

    /**
     * Folds both branches into a single value.
     */
    default <U> U fold(Function<T, U> onSuccess, Function<E, U> onFailure) {
        return switch (this) {
            case Success<T, E> s -> onSuccess.apply(s.value());
            case Failure<T, E> f -> onFailure.apply(f.error());
        };
    }
}
