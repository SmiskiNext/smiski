package io.github.smiskinext.meet;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.shared.openapi.OpenApiGenerationSupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetOpenApiGenerationTest extends OpenApiGenerationSupport {

    @MockitoBean
    JwtDecoder jwtDecoder;
}
