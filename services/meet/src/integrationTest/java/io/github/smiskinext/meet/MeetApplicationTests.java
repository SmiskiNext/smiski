package io.github.smiskinext.meet;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetApplicationTests {

    @Test
    void contextLoads() {}
}
