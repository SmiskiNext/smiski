package io.github.smiskinext.record;

import io.github.smiskinext.record.config.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RecordApplicationTests {

    @Test
    void contextLoads() {}
}
