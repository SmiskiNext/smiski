package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.query.GetHostMeetingsQuery;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.CancelMeetingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.CreateInstantMeetingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.EndMeetingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetHostMeetingsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetMeetingByShortCodeUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetMeetingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.PutMeetingSettingsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.ScheduleMeetingUseCase;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeetingController.class)
@Import(WebConfig.class)
class MeetingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ScheduleMeetingUseCase scheduleMeetingUseCase;

    @MockitoBean
    CreateInstantMeetingUseCase createInstantMeetingUseCase;

    @MockitoBean
    GetMeetingUseCase getMeetingUseCase;

    @MockitoBean
    GetMeetingByShortCodeUseCase getMeetingByShortCodeUseCase;

    @MockitoBean
    GetHostMeetingsUseCase getHostMeetingsUseCase;

    @MockitoBean
    EndMeetingUseCase endMeetingUseCase;

    @MockitoBean
    CancelMeetingUseCase cancelMeetingUseCase;

    @MockitoBean
    PutMeetingSettingsUseCase putMeetingSettingsUseCase;

    @MockitoBean
    CursorTokenEncoder cursorTokenEncoder;

    @Test
    void listHostMeetings_returnsCursorEnvelopeAndNextPageToken() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID firstMeetingId = UUID.randomUUID();
        UUID secondMeetingId = UUID.randomUUID();
        Instant secondCreatedAt = Instant.parse("2026-04-01T09:00:00Z");
        when(getHostMeetingsUseCase.execute(argThat(query -> query.hostId().equals(hostId)
                        && query.pageSize() == 10
                        && query.cursor() == null)))
                .thenReturn(CursorPageResponse.of(
                        List.of(
                                new MeetingResponse(
                                        firstMeetingId,
                                        hostId,
                                        "ABC123",
                                        "Design Review",
                                        "Sprint planning",
                                        Instant.parse("2026-04-02T10:00:00Z"),
                                        Instant.parse("2026-04-02T11:00:00Z"),
                                        MeetingType.SCHEDULED,
                                        MeetingStatus.SCHEDULED,
                                        new MeetingSettingsResponse(
                                                "MANUAL_APPROVAL",
                                                true,
                                                50,
                                                true,
                                                true,
                                                true,
                                                true,
                                                true,
                                                0,
                                                false),
                                        Instant.parse("2026-04-01T08:00:00Z")),
                                new MeetingResponse(
                                        secondMeetingId,
                                        hostId,
                                        "XYZ789",
                                        null,
                                        null,
                                        null,
                                        null,
                                        MeetingType.INSTANT,
                                        MeetingStatus.LIVE,
                                        new MeetingSettingsResponse(
                                                "AUTO_APPROVE",
                                                false,
                                                10,
                                                true,
                                                false,
                                                true,
                                                true,
                                                false,
                                                0,
                                                false),
                                        secondCreatedAt)),
                        10,
                        true));
        when(cursorTokenEncoder.encode(secondCreatedAt, secondMeetingId)).thenReturn("next-token");

        mockMvc.perform(get("/api/v1/meetings")
                        .param("pageSize", "10")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(firstMeetingId.toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(secondMeetingId.toString()))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.nextPageToken").value("next-token"));

        verify(cursorTokenEncoder).encode(secondCreatedAt, secondMeetingId);
    }

    @Test
    void listHostMeetings_decodesPageTokenBeforeCallingUseCase() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        ScrollCursor cursor =
                new ScrollCursor(Instant.parse("2026-04-01T08:00:00Z"), UUID.randomUUID());
        when(cursorTokenEncoder.decode("valid-token")).thenReturn(Result.success(cursor));
        when(getHostMeetingsUseCase.execute(argThat(
                        query -> query.equals(new GetHostMeetingsQuery(hostId, 15, cursor)))))
                .thenReturn(CursorPageResponse.of(
                        List.of(new MeetingResponse(
                                meetingId,
                                hostId,
                                "ABC123",
                                null,
                                null,
                                null,
                                null,
                                MeetingType.INSTANT,
                                MeetingStatus.LIVE,
                                new MeetingSettingsResponse(
                                        "AUTO_APPROVE",
                                        false,
                                        10,
                                        true,
                                        false,
                                        true,
                                        true,
                                        false,
                                        0,
                                        false),
                                Instant.parse("2026-04-01T09:00:00Z"))),
                        15,
                        false));

        mockMvc.perform(get("/api/v1/meetings")
                        .param("pageSize", "15")
                        .param("pageToken", "valid-token")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.nextPageToken").doesNotExist());

        verify(cursorTokenEncoder).decode("valid-token");
    }

    @Test
    void listHostMeetings_returns400ForInvalidPageToken() throws Exception {
        UUID hostId = UUID.randomUUID();
        when(cursorTokenEncoder.decode("bad-token"))
                .thenReturn(Result.failure(CursorErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/meetings")
                        .param("pageToken", "bad-token")
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_CURSOR"));

        verifyNoInteractions(getHostMeetingsUseCase);
    }

    @Test
    void cancelMeeting_returnsOkWhenSuccessful() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(cancelMeetingUseCase.execute(argThat(command -> command.meetingId().equals(meetingId)
                        && command.requesterId().equals(requesterId))))
                .thenReturn(Result.success());

        mockMvc.perform(post("/api/v1/meetings/{id}:cancel", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void cancelMeeting_returns401WhenUnauthenticated() throws Exception {
        UUID meetingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/meetings/{id}:cancel", meetingId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(cancelMeetingUseCase);
    }

    @Test
    void cancelMeeting_returns403WhenRequesterIsNotHost() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(cancelMeetingUseCase.execute(argThat(command -> command.meetingId().equals(meetingId)
                        && command.requesterId().equals(requesterId))))
                .thenReturn(Result.failure(
                        new MeetingError
                                .NotAuthorized(requesterId, hostId)));

        mockMvc.perform(post("/api/v1/meetings/{id}:cancel", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void cancelMeeting_returns404WhenMeetingNotFound() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(cancelMeetingUseCase.execute(argThat(command -> command.meetingId().equals(meetingId)
                        && command.requesterId().equals(requesterId))))
                .thenReturn(Result.failure(
                        new MeetingError
                                .MeetingNotFound(meetingId)));

        mockMvc.perform(post("/api/v1/meetings/{id}:cancel", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void cancelMeeting_returns409WhenMeetingIsAlreadyLive() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(cancelMeetingUseCase.execute(argThat(command -> command.meetingId().equals(meetingId)
                        && command.requesterId().equals(requesterId))))
                .thenReturn(
                        Result.failure(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.LIVE, MeetingStatus.CANCELLED)));

        mockMvc.perform(post("/api/v1/meetings/{id}:cancel", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void putMeetingSettings_returnsOkWhenSuccessful() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(argThat(command -> command.meetingId()
                                .equals(meetingId)
                        && command.requesterId().equals(requesterId)
                        && command.settings().admissionPolicy().name().equals("MANUAL_APPROVAL")
                        && command.settings().allowGuest()
                        && command.settings().maxParticipants() == 40
                        && !command.settings().allowScreenShare()
                        && command.settings().chatEnabled()
                        && command.settings().allowMicrophone()
                        && command.settings().allowVideo()
                        && command.rawPassword() == null)))
                .thenReturn(Result.success(new MeetingSettingsResponse(
                        "MANUAL_APPROVAL", true, 40, false, true, true, true, false, 0, false)));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.admissionPolicy").value("MANUAL_APPROVAL"))
                .andExpect(jsonPath("$.data.allowScreenShare").value(false))
                .andExpect(jsonPath("$.data.requirePassword").value(false));
    }

    @Test
    void putMeetingSettings_missingRequiredField_returns400() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(putMeetingSettingsUseCase);
    }

    @Test
    void putMeetingSettings_blankPassword_returns400() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": true,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(putMeetingSettingsUseCase);
    }

    @Test
    void putMeetingSettings_returns401WhenUnauthenticated() throws Exception {
        UUID meetingId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(putMeetingSettingsUseCase);
    }

    @Test
    void putMeetingSettings_returns403WhenRequesterIsNotHost() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId)
                                && command.requesterId().equals(requesterId))))
                .thenReturn(Result.failure(
                        new MeetingError
                                .NotAuthorized(requesterId, hostId)));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void putMeetingSettings_returns409ForEndedMeeting() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId)
                                && command.requesterId().equals(requesterId))))
                .thenReturn(
                        Result.failure(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.ENDED, MeetingStatus.SCHEDULED)));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void putMeetingSettings_returns409ForCancelledMeeting() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId)
                                && command.requesterId().equals(requesterId))))
                .thenReturn(
                        Result.failure(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.CANCELLED, MeetingStatus.SCHEDULED)));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void putMeetingSettings_returns400ForMaxParticipantsCeilingViolation() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId)
                                && command.requesterId().equals(requesterId))))
                .thenReturn(Result.failure(
                        new MeetingError
                                .InvalidSettings("maxParticipants exceeds ceiling")));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "MANUAL_APPROVAL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_SETTINGS"));
    }

    @Test
    void putMeetingSettings_returns409ForMaxParticipantsBelowActive() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(putMeetingSettingsUseCase.execute(
                        argThat(command -> command.meetingId().equals(meetingId)
                                && command.requesterId().equals(requesterId))))
                .thenReturn(Result.failure(
                        new MeetingError
                                .MeetingFull(meetingId, 40)));

        mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "admissionPolicy": "ALLOW_ALL",
                                  "allowGuest": true,
                                  "maxParticipants": 40,
                                  "allowScreenShare": false,
                                  "chatEnabled": true,
                                  "allowMicrophone": true,
                                  "allowVideo": true,
                                  "password": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("MEETING_FULL"));
    }

    @Test
    void patchMeetingSettings_removed_returns405() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/meetings/{id}/settings", meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
                        .contentType("application/json")
                        .content("{\"allowGuest\":true}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.data.code").value("METHOD_NOT_ALLOWED"));

        verifyNoInteractions(putMeetingSettingsUseCase);
    }
}
