package io.github.smiskinext.meetingmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.phunguy65.zms.shared.domain.CursorTokenEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetingManagementApplicationTests {

    @MockitoBean
    ObjectMapper objectMapper;

    @MockitoBean
    CursorTokenEncoder cursorTokenEncoder;

    @Test
    void contextLoads() {}
}
