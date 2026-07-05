package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.command.AddInviteeCommand;
import io.github.smiskinext.meetingmanagement.application.command.ResendInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeListResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.AddInviteeUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetInviteesUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ResendInviteUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.RevokeInviteUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InviteManagementController.class)
@Import(WebConfig.class)
class InviteManagementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetInviteesUseCase getInviteesUseCase;

    @MockitoBean
    AddInviteeUseCase addInviteeUseCase;

    @MockitoBean
    ResendInviteUseCase resendInviteUseCase;

    @MockitoBean
    RevokeInviteUseCase revokeInviteUseCase;

    @Test
    void getInvitees_returnsListOfInvitees() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant invitedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(getInviteesUseCase.execute(meetingId))
                .thenReturn(Result.success(List.of(new InviteeListResponse(
                        inviteeId,
                        userId,
                        "alice@example.com",
                        "Alice",
                        InviteeStatus.PENDING,
                        invitedAt,
                        null,
                        "PENDING"))));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].inviteeId").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.data[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].tokenStatus").value("PENDING"));
    }

    @Test
    void getInvitees_returnsEmptyListWhenNoInvitees() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(getInviteesUseCase.execute(meetingId)).thenReturn(Result.success(List.of()));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getInvitees_returnsNotFoundWhenMeetingDoesNotExist() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(getInviteesUseCase.execute(meetingId))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void addInvitee_returns201AndInviteeDetails() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant invitedAt = Instant.now();
        when(addInviteeUseCase.execute(any(AddInviteeCommand.class)))
                .thenReturn(Result.success(new InviteeListResponse(
                        inviteeId,
                        userId,
                        "bob@example.com",
                        "Bob",
                        InviteeStatus.PENDING,
                        invitedAt,
                        null,
                        "PENDING")));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "bob@example.com", "displayName": "Bob"}
                                """)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.inviteeId").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data.email").value("bob@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Bob"))
                .andExpect(jsonPath("$.data.tokenStatus").value("PENDING"));
    }

    @Test
    void addInvitee_returns400ForInvalidEmail() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "displayName": "Bad"}
                                """)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void addInvitee_returns400ForMissingEmail() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "No Email"}
                                """)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void addInvitee_returns403WhenRequesterIsNotHost() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID nonHostId = UUID.randomUUID();
        when(addInviteeUseCase.execute(any(AddInviteeCommand.class)))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(nonHostId, UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "eve@example.com"}
                                """)
                        .principal(new TestingAuthenticationToken(nonHostId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void addInvitee_returns422WhenInviteeNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(addInviteeUseCase.execute(any(AddInviteeCommand.class)))
                .thenReturn(
                        Result.failure(new MeetingError.InviteeNotFound("unknown@example.com")));

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "unknown@example.com"}
                                """)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void resendInvite_returns200AndUpdatedInvitee() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Instant invitedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(resendInviteUseCase.execute(any(ResendInviteCommand.class)))
                .thenReturn(Result.success(new InviteeListResponse(
                        inviteeId,
                        null,
                        "alice@example.com",
                        "Alice",
                        InviteeStatus.PENDING,
                        invitedAt,
                        null,
                        "PENDING")));

        mockMvc.perform(post(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.inviteeId").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data.tokenStatus").value("PENDING"));
    }

    @Test
    void resendInvite_returns403WhenRequesterIsNotHost() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID nonHostId = UUID.randomUUID();
        when(resendInviteUseCase.execute(any(ResendInviteCommand.class)))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(nonHostId, UUID.randomUUID())));

        mockMvc.perform(post(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(nonHostId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void resendInvite_returns404WhenInviteeNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(resendInviteUseCase.execute(any(ResendInviteCommand.class)))
                .thenReturn(Result.failure(new MeetingError.InviteeNotFound(inviteeId.toString())));

        mockMvc.perform(post(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void revokeInvite_returns200AndRevokedInvitee() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Instant invitedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(revokeInviteUseCase.execute(any(), any(), any()))
                .thenReturn(Result.success(new InviteeListResponse(
                        inviteeId,
                        null,
                        "alice@example.com",
                        "Alice",
                        InviteeStatus.PENDING,
                        invitedAt,
                        null,
                        "REVOKED")));

        mockMvc.perform(delete(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.inviteeId").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data.tokenStatus").value("REVOKED"));
    }

    @Test
    void revokeInvite_returns403WhenRequesterIsNotHost() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID nonHostId = UUID.randomUUID();
        when(revokeInviteUseCase.execute(any(), any(), any()))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(nonHostId, UUID.randomUUID())));

        mockMvc.perform(delete(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(nonHostId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void revokeInvite_returns404WhenInviteeNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(revokeInviteUseCase.execute(any(), any(), any()))
                .thenReturn(Result.failure(new MeetingError.InviteeNotFound(inviteeId.toString())));

        mockMvc.perform(delete(
                                "/api/v1/meetings/{meetingId}/invitees/{inviteeId}",
                                meetingId,
                                inviteeId)
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    void unauthenticatedRequests_return401() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/meetings/{meetingId}/invitees", meetingId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/meetings/{meetingId}/invitees", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "x@example.com"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                        "/api/v1/meetings/{meetingId}/invitees/{inviteeId}/resend",
                        meetingId,
                        inviteeId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(
                        "/api/v1/meetings/{meetingId}/invitees/{inviteeId}", meetingId, inviteeId))
                .andExpect(status().isUnauthorized());
    }
}
