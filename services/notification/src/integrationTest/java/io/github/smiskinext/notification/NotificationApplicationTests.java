package io.github.smiskinext.notification;

import io.github.smiskinext.notification.config.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationApplicationTests {

    @Test
    void contextLoads() {}
}
