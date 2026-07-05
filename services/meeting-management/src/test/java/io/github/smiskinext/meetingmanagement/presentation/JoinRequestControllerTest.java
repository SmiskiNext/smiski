package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.response.JoinRequestResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.ApproveAllJoinRequestsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ApproveJoinRequestUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.DenyJoinRequestUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetJoinRequestsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.sse.MeetingSseManager;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JoinRequestController.class)
@Import(WebConfig.class)
class JoinRequestControllerTest {

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

    @Test
    void listJoinRequests_returnsOffsetEnvelope() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(getJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.success(OffsetPageResponse.of(
                        List.of(new JoinRequestResponse(
                                requestId,
                                meetingId,
                                null,
                                "Guest One",
                                null,
                                JoinRequestStatus.PENDING,
                                Instant.parse("2026-04-01T10:00:00Z"),
                                Instant.parse("2026-04-01T10:02:00Z"))),
                        10,
                        20,
                        true)));

        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", meetingId)
                        .param("pageSize", "10")
                        .param("offset", "20")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.data.content[0].displayName").value("Guest One"))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.nextOffset").value(30));

        verify(getJoinRequestsUseCase).execute(any());
    }

    @Test
    void listJoinRequests_returns403OnAuthorizationFailure() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(getJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(UUID.randomUUID(), UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void listJoinRequests_omitsNextOffsetOnLastPage() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(getJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.success(OffsetPageResponse.of(List.of(), 10, 20, false)));

        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", meetingId)
                        .param("pageSize", "10")
                        .param("offset", "20")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.nextOffset").doesNotExist());
    }

    @Test
    void listJoinRequests_respectsCustomPageSize() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(getJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.success(OffsetPageResponse.of(List.of(), 25, 0, false)));

        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", meetingId)
                        .param("pageSize", "25")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.nextOffset").doesNotExist());

        verify(getJoinRequestsUseCase).execute(any());
    }

    @Test
    void listJoinRequests_returns404WhenMeetingDoesNotExist() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(getJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void listJoinRequests_invalidMeetingIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/{id}/joinRequests", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getJoinRequestsUseCase);
    }

    @Test
    void approveAllJoinRequests_partialFailureReturns207WithApprovedCountAndFailedIds()
            throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID failedId1 = UUID.randomUUID();
        UUID failedId2 = UUID.randomUUID();

        when(approveAllJoinRequestsUseCase.execute(any()))
                .thenReturn(Result.failure(
                        new MeetingError.PartialApprovalFailure(3, List.of(failedId1, failedId2))));

        mockMvc.perform(post("/api/v1/meetings/{id}/joinRequests:approveAll", meetingId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("PARTIAL_APPROVAL_FAILURE"))
                .andExpect(jsonPath("$.data.approvedCount").value(3))
                .andExpect(jsonPath("$.data.failedIds[0]").value(failedId1.toString()))
                .andExpect(jsonPath("$.data.failedIds[1]").value(failedId2.toString()))
                .andExpect(
                        jsonPath("$.data.message").value("Partial approval: 3 approved, 2 failed"));
    }
}
