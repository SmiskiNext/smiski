package io.github.smiskinext.shared.application;

import io.github.smiskinext.shared.domain.Result;

/**
 * Inbound port contract for application use cases.
 *
 * @param <I> the input type (a {@link Command} or {@link Query})
 * @param <O> the success output type
 * @param <E> the failure/error type
 */
public interface UseCase<I, O, E> {

    Result<O, E> execute(I input);
}
