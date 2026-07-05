package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.response.ParticipantListItemResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.GetParticipantsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.KickParticipantUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.MuteAllParticipantsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.MuteParticipantTrackUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
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

@WebMvcTest(ParticipantController.class)
@Import(WebConfig.class)
class ParticipantControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetParticipantsUseCase getParticipantsUseCase;

    @MockitoBean
    KickParticipantUseCase kickParticipantUseCase;

    @MockitoBean
    MuteAllParticipantsUseCase muteAllParticipantsUseCase;

    @MockitoBean
    MuteParticipantTrackUseCase muteParticipantTrackUseCase;

    @Test
    void getParticipants_returnsSimpleListResponse() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(getParticipantsUseCase.execute(any()))
                .thenReturn(Result.success(List.of(new ParticipantListItemResponse(
                        21L,
                        meetingId,
                        userId,
                        "Alice",
                        null,
                        ParticipantRole.HOST,
                        Instant.parse("2026-04-01T10:00:00Z"),
                        null))));

        mockMvc.perform(get("/api/v1/meetings/{id}/participants", meetingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.data[0].role").value("HOST"))
                .andExpect(jsonPath("$.data[0].joinedAt").value("2026-04-01T10:00:00Z"))
                .andExpect(jsonPath("$.data[0].leftAt").isEmpty())
                .andExpect(jsonPath("$.data.nextPageToken").doesNotExist());

        verify(getParticipantsUseCase).execute(any());
    }

    @Test
    void getParticipants_paginationParamsAreIgnored() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(getParticipantsUseCase.execute(any())).thenReturn(Result.success(List.of()));

        mockMvc.perform(get("/api/v1/meetings/{id}/participants", meetingId)
                        .param("pageSize", "10")
                        .param("pageToken", "legacy-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getParticipants_meetingNotFound_returns404() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(getParticipantsUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(get("/api/v1/meetings/{id}/participants", meetingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void getParticipants_multipleParticipants_returnsAllInJsonArray() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(getParticipantsUseCase.execute(any()))
                .thenReturn(Result.success(List.of(
                        new ParticipantListItemResponse(
                                1L,
                                meetingId,
                                UUID.randomUUID(),
                                "Host",
                                null,
                                ParticipantRole.HOST,
                                Instant.parse("2026-04-01T10:00:00Z"),
                                null),
                        new ParticipantListItemResponse(
                                2L,
                                meetingId,
                                null,
                                "Guest",
                                null,
                                ParticipantRole.GUEST,
                                Instant.parse("2026-04-01T10:05:00Z"),
                                Instant.parse("2026-04-01T10:30:00Z")))));

        mockMvc.perform(get("/api/v1/meetings/{id}/participants", meetingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].displayName").value("Host"))
                .andExpect(jsonPath("$.data[1].displayName").value("Guest"))
                .andExpect(jsonPath("$.data[1].userId").isEmpty())
                .andExpect(jsonPath("$.data[1].leftAt").value("2026-04-01T10:30:00Z"));
    }

    @Test
    void getParticipants_invalidMeetingId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/{id}/participants", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getParticipantsUseCase);
    }

    @Test
    void kickParticipant_byUserId_returns204() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any())).thenReturn(Result.success());

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", targetUserId.toString())
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNoContent());

        verify(kickParticipantUseCase).execute(any());
    }

    @Test
    void kickParticipant_byDisplayName_returns204() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any())).thenReturn(Result.success());

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("displayName", "Bob Guest")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNoContent());
    }

    @Test
    void kickParticipant_notHost_returns403() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any()))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(UUID.randomUUID(), UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", targetUserId.toString())
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void kickParticipant_inactiveTarget_returns404() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any()))
                .thenReturn(Result.failure(
                        new MeetingError.UserNotInMeeting(meetingId, targetUserId.toString())));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", targetUserId.toString())
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("USER_NOT_IN_MEETING"));
    }

    @Test
    void kickParticipant_selfKick_returns400() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.CanNotKickSelf(meetingId, hostId)));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", hostId.toString())
                        .principal(new TestingAuthenticationToken(hostId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("CAN_NOT_KICK_SELF"));
    }

    @Test
    void kickParticipant_meetingNotLive_returns409() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.InvalidStatusTransition(
                        MeetingStatus
                                .SCHEDULED,
                        MeetingStatus
                                .LIVE)));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", targetUserId.toString())
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void kickParticipant_noAuthentication_returns401() throws Exception {
        UUID meetingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(kickParticipantUseCase);
    }

    @Test
    void kickParticipant_invalidTarget_returns400() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(kickParticipantUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.InvalidKickTarget(
                        "either userId or displayName must be provided")));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:kick", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_KICK_TARGET"));
    }

    @Test
    void muteAllParticipants_asHost_returns204() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(muteAllParticipantsUseCase.execute(any())).thenReturn(Result.success());

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:muteAll", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNoContent());

        verify(muteAllParticipantsUseCase).execute(any());
    }

    @Test
    void muteAllParticipants_notHost_returns403() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(muteAllParticipantsUseCase.execute(any()))
                .thenReturn(Result.failure(
                        new MeetingError.NotAuthorized(UUID.randomUUID(), UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:muteAll", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void muteAllParticipants_meetingNotFound_returns404() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(muteAllParticipantsUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:muteAll", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void muteParticipantTrack_validRequest_returns204() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = "user01:device-A";
        when(muteParticipantTrackUseCase.execute(any())).thenReturn(Result.success());

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNoContent());

        verify(muteParticipantTrackUseCase).execute(any());
    }

    @Test
    void muteParticipantTrack_hostTargetsSelf_returns422() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = UUID.randomUUID() + ":device-001";
        when(muteParticipantTrackUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.CanNotMuteSelf()));

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("CANNOT_MUTE_SELF"));
    }

    @Test
    void muteParticipantTrack_trackNotFound_returns422() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = "user01:device-A";
        when(muteParticipantTrackUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.TrackNotFound(identity, "microphone")));

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("TRACK_NOT_FOUND"));
    }

    @Test
    void muteParticipantTrack_noAuthentication_returns401() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = "user01:device-A";

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(muteParticipantTrackUseCase);
    }

    @Test
    void muteAllParticipants_meetingNotLive_returns422() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(muteAllParticipantsUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.MeetingNotLive(meetingId)));

        mockMvc.perform(post("/api/v1/meetings/{id}/participants:muteAll", meetingId)
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void muteParticipantTrack_meetingNotLive_returns422() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = "user01:device-A";
        when(muteParticipantTrackUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.MeetingNotLive(meetingId)));

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void muteParticipantTrack_participantNotInRoom_returns404() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String identity = "user01:device-A";
        when(muteParticipantTrackUseCase.execute(any()))
                .thenReturn(Result.failure(new MeetingError.LiveKitParticipantNotFound(
                        "room-" + meetingId, identity)));

        mockMvc.perform(post(
                                "/api/v1/meetings/{id}/participants/{identity}:muteTrack",
                                meetingId,
                                identity)
                        .param("source", "microphone")
                        .principal(
                                new TestingAuthenticationToken(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("PARTICIPANT_NOT_FOUND"));
    }
}
