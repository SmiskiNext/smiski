package io.github.smiskinext.shared.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class));

    @Test
    void relay_stays_inactive_when_no_outbox_store_bean_present() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OutboxRelay.class);
            assertThat(context).doesNotHaveBean(OutboxRelayTrigger.class);
            assertThat(context).doesNotHaveBean(OutboxTransport.class);
        });
    }
}
