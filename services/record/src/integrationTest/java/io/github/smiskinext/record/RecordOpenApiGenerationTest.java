package io.github.smiskinext.record;

import io.github.smiskinext.record.config.TestcontainersConfiguration;
import io.github.smiskinext.shared.openapi.OpenApiGenerationSupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RecordOpenApiGenerationTest extends OpenApiGenerationSupport {}
