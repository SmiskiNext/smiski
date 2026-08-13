package io.github.smiskinext.notification.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import io.github.smiskinext.notification.application.result.SubscribeMeetingEventsResult;
import io.github.smiskinext.notification.application.usecase.SubscribeMeetingEventsUseCase;
import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.shared.domain.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeetingEventsCorsIntegrationTest {

    private static final String FORGE_ORIGIN = "https://some-tenant.atlassian.net";
    private static final UUID MEETING_ID = UUID.fromString("22222222-2222-7222-9222-222222222222");
    private static final UUID REQUEST_ID = UUID.fromString("019ff908-f0d3-79d8-896e-beb8f922efd8");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscribeMeetingEventsUseCase subscribeMeetingEventsUseCase;

    @Test
    void meetingEventStreamAnswersOriginWithAllowOriginHeader() throws Exception {
        stubSubscribeWithEmitter();

        mockMvc.perform(get("/api/1/meetings/{id}/events", MEETING_ID)
                        .header(HttpHeaders.ORIGIN, FORGE_ORIGIN)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FORGE_ORIGIN));
    }

    @Test
    void joinRequestEventStreamAnswersOriginWithAllowOriginHeader() throws Exception {
        stubSubscribeWithEmitter();

        mockMvc.perform(get(
                                "/api/1/meetings/{id}/join-requests/{requestId}/events",
                                MEETING_ID,
                                REQUEST_ID)
                        .header(HttpHeaders.ORIGIN, FORGE_ORIGIN)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FORGE_ORIGIN));
    }

    private void stubSubscribeWithEmitter() {
        when(subscribeMeetingEventsUseCase.execute(any()))
                .thenReturn(Result.success(new SubscribeMeetingEventsResult(new SseEmitter())));
    }
}
