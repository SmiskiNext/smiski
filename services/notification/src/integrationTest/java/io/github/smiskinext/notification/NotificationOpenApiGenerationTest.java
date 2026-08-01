package io.github.smiskinext.notification;

import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.shared.openapi.OpenApiGenerationSupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationOpenApiGenerationTest extends OpenApiGenerationSupport {}
