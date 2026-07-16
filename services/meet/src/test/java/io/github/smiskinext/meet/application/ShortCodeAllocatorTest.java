package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.helper.ShortCodeAllocator;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCodeCollisionException;
import io.github.smiskinext.meet.domain.port.ShortCodeGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ShortCodeAllocatorTest {

    private ShortCodeGenerator generator;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        generator = mock(ShortCodeGenerator.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any()))
                .thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void returnsResultOnFirstAttemptWhenNoCollision() {
        when(generator.generate()).thenReturn(ShortCode.of("aaaaaaaaaa"));
        ShortCodeAllocator allocator =
                new ShortCodeAllocator(generator, transactionManager, () -> 10);

        Optional<String> result = allocator.allocate(ShortCode::value);

        assertThat(result).contains("aaaaaaaaaa");
    }

    @Test
    void retriesWithFreshCodeAfterCollision() {
        when(generator.generate())
                .thenReturn(ShortCode.of("collide001"))
                .thenReturn(ShortCode.of("success002"));
        ShortCodeAllocator allocator =
                new ShortCodeAllocator(generator, transactionManager, () -> 10);

        List<String> seen = new ArrayList<>();
        Optional<String> result = allocator.allocate(code -> {
            seen.add(code.value());
            if (seen.size() == 1) {
                throw new ShortCodeCollisionException(code.value(), new RuntimeException());
            }
            return code.value();
        });

        assertThat(result).contains("success002");
        assertThat(seen).containsExactly("collide001", "success002");
    }

    @Test
    void returnsEmptyWhenEveryAttemptCollides() {
        when(generator.generate()).thenReturn(ShortCode.of("collide999"));
        ShortCodeAllocator allocator =
                new ShortCodeAllocator(generator, transactionManager, () -> 3);

        int[] attempts = {0};
        Optional<String> result = allocator.allocate(code -> {
            attempts[0]++;
            throw new ShortCodeCollisionException(code.value(), new RuntimeException());
        });

        assertThat(result).isEmpty();
        assertThat(attempts[0]).isEqualTo(3);
    }
}
