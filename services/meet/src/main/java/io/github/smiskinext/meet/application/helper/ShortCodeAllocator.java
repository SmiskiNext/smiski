package io.github.smiskinext.meet.application.helper;

import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCodeCollisionException;
import io.github.smiskinext.meet.domain.port.ShortCodeAllocationSettings;
import io.github.smiskinext.meet.domain.port.ShortCodeGenerator;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Allocates a unique meeting {@link ShortCode} using an insert-and-catch strategy.
 *
 * <p>Rather than checking for existence before insert (which races with concurrent creates), the
 * caller's action attempts to persist a meeting carrying a freshly generated code inside its own
 * transaction. If the short-code unique constraint rejects the insert, the persistence adapter
 * raises {@link ShortCodeCollisionException}; this allocator rolls the attempt back and retries with
 * a new code, up to {@link ShortCodeAllocationSettings#maxAttempts()} times.
 *
 * <p>Each attempt runs in a dedicated transaction so a collision on one attempt never poisons the
 * next. The action is reusable across meeting creation flows (instant and scheduled); it must
 * perform all persistence for the attempt so that a rollback discards it atomically.
 */
@Component
public class ShortCodeAllocator {

    private final ShortCodeGenerator shortCodeGenerator;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;

    public ShortCodeAllocator(
            ShortCodeGenerator shortCodeGenerator,
            PlatformTransactionManager transactionManager,
            ShortCodeAllocationSettings settings) {
        this.shortCodeGenerator = shortCodeGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxAttempts = settings.maxAttempts();
    }

    /**
     * Runs {@code action} with generated codes until it succeeds without a collision.
     *
     * @param action persists the meeting (and related state) for the supplied code and returns the
     *     use-case outcome; it must not swallow {@link ShortCodeCollisionException}
     * @param <T> use-case result type
     * @return the action's result, or {@link Optional#empty()} when every attempt collided
     */
    public <T> Optional<T> allocate(Function<ShortCode, T> action) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ShortCode candidate = shortCodeGenerator.generate();
            try {
                return Optional.of(transactionTemplate.execute(status -> action.apply(candidate)));
            } catch (ShortCodeCollisionException collision) {
                // Attempt rolled back; retry with a fresh code.
            }
        }
        return Optional.empty();
    }
}
