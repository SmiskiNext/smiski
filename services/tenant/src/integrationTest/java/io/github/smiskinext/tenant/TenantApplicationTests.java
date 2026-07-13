package io.github.smiskinext.tenant;

import io.github.smiskinext.tenant.config.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantApplicationTests {

    @Test
    void contextLoads() {}
}
