package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.command.InviteeResponseType;
import io.github.smiskinext.meetingmanagement.application.command.RespondInviteCommand;
import io.github.smiskinext.meetingmanagement.application.response.InviteeRespondResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.RespondInviteUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InviteeResponseController.class)
@Import(WebConfig.class)
class InviteeResponseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RespondInviteUseCase respondInviteUseCase;

    @Test
    void respondInvite_acceptsInvitation() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant respondedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(respondInviteUseCase.execute(
                        argThat(command -> command.equals(new RespondInviteCommand(
                                meetingId, userId, userId, InviteeResponseType.ACCEPTED)))))
                .thenReturn(Result.success(new InviteeRespondResponse(
                        meetingId, userId, InviteeStatus.ACCEPTED, respondedAt)));

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"ACCEPTED"}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void respondInvite_declinesInvitation() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant respondedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(respondInviteUseCase.execute(
                        argThat(command -> command.equals(new RespondInviteCommand(
                                meetingId, userId, userId, InviteeResponseType.DECLINED)))))
                .thenReturn(Result.success(new InviteeRespondResponse(
                        meetingId, userId, InviteeStatus.DECLINED, respondedAt)));

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"DECLINED"}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
    }

    @Test
    void respondInvite_returns404WhenMeetingMissing() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(respondInviteUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId))))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"ACCEPTED"}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void respondInvite_returns404WhenInviteeMissing() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(respondInviteUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId))))
                .thenReturn(Result.failure(new MeetingError.InviteeNotFound(userId.toString())));

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"ACCEPTED"}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("INVITEE_NOT_FOUND"));
    }

    @Test
    void respondInvite_returns409WhenAlreadyResponded() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(respondInviteUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId))))
                .thenReturn(Result.failure(new MeetingError.InvalidInviteeTransition(
                        InviteeStatus.ACCEPTED, InviteeStatus.DECLINED)));

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"DECLINED"}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("INVALID_INVITEE_TRANSITION"));
    }

    @Test
    void respondInvite_returns400ForInvalidRequest() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":null}
                                """)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(respondInviteUseCase);
    }

    @Test
    void respondInvite_returns401WithoutPrincipal() throws Exception {
        mockMvc.perform(patch("/api/v1/meetings/{meetingId}/invitees/me", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"ACCEPTED"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
