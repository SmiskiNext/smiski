package io.github.smiskinext.tenant;

import io.github.smiskinext.shared.openapi.OpenApiGenerationSupport;
import io.github.smiskinext.tenant.config.TestcontainersConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantOpenApiGenerationTest extends OpenApiGenerationSupport {}
