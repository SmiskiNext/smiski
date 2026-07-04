package io.github.smiskinext.meetingmanagement.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.response.RequestJoinResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.ActivateRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ApproveAllJoinRequestsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ApproveJoinRequestUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.AssignSidUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.CloseStaleMeetingLogsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.DenyJoinRequestUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.FinalizeRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetJoinRequestsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.LeaveMeetingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.port.EventPublisher;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.MeetingSseManager;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.meetingmanagement.presentation.JoinRequestController;
import io.github.smiskinext.meetingmanagement.presentation.LiveKitWebhookController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest({JoinRequestController.class, LiveKitWebhookController.class})
@Import({SecurityConfig.class, WebConfig.class, PublicEndpointSecurityTest.TestConfig.class})
class PublicEndpointSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RequestJoinUseCase requestJoinUseCase;

    @MockitoBean
    ApproveJoinRequestUseCase approveJoinRequestUseCase;

    @MockitoBean
    DenyJoinRequestUseCase denyJoinRequestUseCase;

    @MockitoBean
    ApproveAllJoinRequestsUseCase approveAllJoinRequestsUseCase;

    @MockitoBean
    GetJoinRequestsUseCase getJoinRequestsUseCase;

    @MockitoBean
    MeetingSseManager meetingSseManager;

    @MockitoBean
    AssignSidUseCase assignSidUseCase;

    @MockitoBean
    LeaveMeetingUseCase leaveMeetingUseCase;

    @MockitoBean
    CloseStaleMeetingLogsUseCase closeStaleMeetingLogsUseCase;

    @MockitoBean
    ActivateRecordingUseCase activateRecordingUseCase;

    @MockitoBean
    FinalizeRecordingUseCase finalizeRecordingUseCase;

    @MockitoBean
    EventPublisher eventPublisher;

    @MockitoBean
    ParticipationLogRepository
            participationLogRepository;

    @ParameterizedTest
    @ValueSource(
            strings = {"/api/v1/meetings/{id}:requestJoin", "/api/1.0/meetings/{id}:requestJoin"})
    void requestJoin_publicVersionedRouteIsNotBlockedBySecurity(String path) throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(requestJoinUseCase.execute(any()))
                .thenReturn(io.github.phunguy65.zms.shared.domain.Result.success(
                        new RequestJoinResponse(requestId, JoinRequestStatus.PENDING, null, null)));

        mockMvc.perform(post(path, meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Guest One",
                                  "deviceId": "device-123"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void guestJoinRequestEvents_publicVersionedRouteIsNotBlockedBySecurity() throws Exception {
        when(meetingSseManager.subscribeGuest(any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/joinRequests/{requestId}/events", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/webhook/livekit", "/api/1.0/webhook/livekit"})
    void livekitWebhook_supportedVersionedRoutesReachControllerInsteadOfSecurity401(String path)
            throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "invalid-token")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateVersionedEndpointStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestJoinWithoutApiVersionPrefixIsNotTreatedAsPublicRoute() throws Exception {
        mockMvc.perform(post("/meetings/{id}:requestJoin", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Guest One",
                                  "deviceId": "device-123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestJoinWithApiPrefixButNoVersionIsNotTreatedAsPublicRoute() throws Exception {
        mockMvc.perform(post("/api/meetings/{id}:requestJoin", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Guest One",
                                  "deviceId": "device-123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        HeaderAuthFilter headerAuthFilter() {
            return new HeaderAuthFilter();
        }

        @Bean
        LiveKitProperties liveKitProperties() {
            LiveKitProperties properties = new LiveKitProperties();
            properties.setApiKey("test-api-key");
            properties.setApiSecret("test-api-secret-which-is-long-enough-for-hs256");
            return properties;
        }
    }
}
