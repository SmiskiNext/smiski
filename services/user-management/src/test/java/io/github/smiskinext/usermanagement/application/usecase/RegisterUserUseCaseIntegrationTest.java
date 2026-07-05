package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.usermanagement.application.command.RegisterCommand;
import io.github.smiskinext.usermanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.usermanagement.infrastructure.messaging.KafkaEventPublisher;
import io.github.smiskinext.usermanagement.infrastructure.messaging.OutboxEventPublisher;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventRepository;
import io.github.smiskinext.usermanagement.infrastructure.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test verifying that RegisterUserUseCase wires correctly with the Spring context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RegisterUserUseCaseIntegrationTest {

    @Autowired
    RegisterUserUseCase registerUserUseCase;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    // Mock Kafka infrastructure — no real broker needed in tests
    @MockitoBean
    KafkaEventPublisher kafkaEventPublisher;

    @MockitoBean
    OutboxEventPublisher outboxEventPublisher;

    // Mock Firebase — no credentials needed in tests
    @MockitoBean
    FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void successfulRegistration_contextLoadsAndUseCaseIsWired() {
        var cmd = new RegisterCommand(
                "integration-" + System.nanoTime() + "@example.com",
                "password123",
                "Integration User",
                "intuser_" + System.nanoTime() % 100000);

        var result = registerUserUseCase.execute(cmd);

        assertThat(result).isInstanceOf(Result.Success.class);
    }
}
