package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingDetailQuery;
import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingsQuery;
import io.github.smiskinext.meetingmanagement.application.query.GetPendingInvitationsQuery;
import io.github.smiskinext.meetingmanagement.application.response.InviteeResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingDetailResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingParticipantResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.application.response.ParticipatedMeetingListItemResponse;
import io.github.smiskinext.meetingmanagement.application.response.ParticipatedMeetingPageResponse;
import io.github.smiskinext.meetingmanagement.application.response.PendingInvitationResponse;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.GetParticipatedMeetingDetailUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetParticipatedMeetingsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetPendingInvitationsUseCase;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.InviteeStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meetingmanagement.infrastructure.web.ParticipatedMeetingCursorCodec;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserMeetingController.class)
@Import(WebConfig.class)
class UserMeetingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetParticipatedMeetingsUseCase getParticipatedMeetingsUseCase;

    @MockitoBean
    GetParticipatedMeetingDetailUseCase getParticipatedMeetingDetailUseCase;

    @MockitoBean
    GetPendingInvitationsUseCase getPendingInvitationsUseCase;

    @MockitoBean
    ParticipatedMeetingCursorCodec participatedMeetingCursorCodec;

    @Test
    void listPendingInvitations_returnsInvitations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant invitedAt = Instant.parse("2026-04-28T10:00:00Z");
        when(getPendingInvitationsUseCase.execute(new GetPendingInvitationsQuery(userId, userId)))
                .thenReturn(Result.success(List.of(new PendingInvitationResponse(
                        UUID.randomUUID(),
                        meetingId,
                        "Planning",
                        "ABC123",
                        Instant.parse("2026-05-01T10:00:00Z"),
                        "Host User",
                        invitedAt))));

        mockMvc.perform(get("/api/v1/users/{userId}/invitations:pending", userId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.data[0].meetingTitle").value("Planning"));
    }

    @Test
    void listPendingInvitations_returns403ForWrongUserScope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(getPendingInvitationsUseCase.execute(
                        new GetPendingInvitationsQuery(userId, requesterId)))
                .thenReturn(Result.failure(new MeetingError.NotOwner(requesterId, userId)));

        mockMvc.perform(get("/api/v1/users/{userId}/invitations:pending", userId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("NOT_OWNER"));
    }

    @Test
    void listPendingInvitations_returns401WithoutPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/invitations:pending", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listParticipatedMeetings_returnsCursorEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant lastJoinedAt = Instant.parse("2026-04-01T09:00:00Z");
        when(getParticipatedMeetingsUseCase.execute(
                        argThat(query -> query.equals(new GetParticipatedMeetingsQuery(
                                userId, userId, Set.of(MeetingStatus.ENDED), 10, null)))))
                .thenReturn(Result.success(new ParticipatedMeetingPageResponse(
                        List.of(new ParticipatedMeetingListItemResponse(
                                lastJoinedAt,
                                new MeetingResponse(
                                        meetingId,
                                        UUID.randomUUID(),
                                        "ABC123",
                                        "Retro",
                                        null,
                                        null,
                                        null,
                                        MeetingType.INSTANT,
                                        MeetingStatus.ENDED,
                                        new MeetingSettingsResponse(
                                                "ALLOW_ALL",
                                                true,
                                                10,
                                                true,
                                                true,
                                                true,
                                                true,
                                                false,
                                                0,
                                                false),
                                        Instant.parse("2026-04-01T08:00:00Z")))),
                        10,
                        true)));
        when(participatedMeetingCursorCodec.encode(
                        new ParticipatedMeetingCursor(lastJoinedAt, meetingId)))
                .thenReturn("next-token");

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("pageSize", "10")
                        .param("status", "ENDED")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(meetingId.toString()))
                .andExpect(jsonPath("$.data.nextPageToken").value("next-token"));
    }

    @Test
    void listParticipatedMeetings_acceptsMultipleStatuses() throws Exception {
        UUID userId = UUID.randomUUID();
        when(getParticipatedMeetingsUseCase.execute(
                        argThat(query -> query.equals(new GetParticipatedMeetingsQuery(
                                userId,
                                userId,
                                Set.of(MeetingStatus.ENDED, MeetingStatus.LIVE),
                                20,
                                null)))))
                .thenReturn(
                        Result.success(new ParticipatedMeetingPageResponse(List.of(), 20, false)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("status", "ENDED,LIVE")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void listParticipatedMeetings_returnsNoNextTokenWhenHasNoNextPage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(getParticipatedMeetingsUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingsQuery(userId, userId, Set.of(), 20, null)))))
                .thenReturn(Result.success(new ParticipatedMeetingPageResponse(
                        List.of(new ParticipatedMeetingListItemResponse(
                                Instant.parse("2026-04-01T09:00:00Z"),
                                new MeetingResponse(
                                        meetingId,
                                        UUID.randomUUID(),
                                        "ABC123",
                                        null,
                                        null,
                                        null,
                                        null,
                                        MeetingType.INSTANT,
                                        MeetingStatus.LIVE,
                                        new MeetingSettingsResponse(
                                                "ALLOW_ALL",
                                                true,
                                                10,
                                                true,
                                                true,
                                                true,
                                                true,
                                                false,
                                                0,
                                                false),
                                        Instant.parse("2026-04-01T08:00:00Z")))),
                        20,
                        false)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextPageToken").doesNotExist());
    }

    @Test
    void listParticipatedMeetings_returns400ForInvalidStatus() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("status", "NOPE")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(getParticipatedMeetingsUseCase);
    }

    @Test
    void listParticipatedMeetings_treatsBlankStatusAsNoFilter() throws Exception {
        UUID userId = UUID.randomUUID();
        when(getParticipatedMeetingsUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingsQuery(userId, userId, Set.of(), 20, null)))))
                .thenReturn(
                        Result.success(new ParticipatedMeetingPageResponse(List.of(), 20, false)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("status", "   ")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk());
    }

    @Test
    void listParticipatedMeetings_returns401WithoutPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void listParticipatedMeetings_returns400ForInvalidPageToken() throws Exception {
        UUID userId = UUID.randomUUID();
        when(participatedMeetingCursorCodec.decode("bad-token"))
                .thenReturn(Result.failure(CursorErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("pageToken", "bad-token")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_CURSOR"));
    }

    @Test
    void listParticipatedMeetings_decodesPageTokenIntoParticipatedCursor() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        ParticipatedMeetingCursor cursor =
                new ParticipatedMeetingCursor(Instant.parse("2026-04-01T09:00:00Z"), meetingId);
        when(participatedMeetingCursorCodec.decode("valid-token"))
                .thenReturn(Result.success(cursor));
        when(getParticipatedMeetingsUseCase.execute(
                        argThat(query -> query.equals(new GetParticipatedMeetingsQuery(
                                userId, userId, Set.of(MeetingStatus.ENDED), 15, cursor)))))
                .thenReturn(
                        Result.success(new ParticipatedMeetingPageResponse(List.of(), 15, false)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings:filter", userId)
                        .param("status", "ENDED")
                        .param("pageSize", "15")
                        .param("pageToken", "valid-token")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk());
    }

    @Test
    void getParticipatedMeetingDetail_returnsAggregatedDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(getParticipatedMeetingDetailUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingDetailQuery(userId, meetingId, userId)))))
                .thenReturn(Result.success(new MeetingDetailResponse(
                        meetingId,
                        UUID.randomUUID(),
                        "ABC123",
                        "Retro",
                        "Wrap up",
                        null,
                        null,
                        MeetingType.SCHEDULED,
                        MeetingStatus.ENDED,
                        new MeetingSettingsResponse(
                                "ALLOW_ALL", true, 10, true, true, true, true, false, 0, false),
                        Instant.parse("2026-04-01T08:00:00Z"),
                        List.of(new MeetingParticipantResponse(
                                meetingId,
                                userId,
                                "Alice",
                                null,
                                ParticipantRole.PARTICIPANT,
                                Instant.parse("2026-04-01T10:00:00Z"),
                                null)),
                        List.of(new RecordingResponse(
                                UUID.randomUUID(),
                                meetingId,
                                "https://example.com/recording.mp4",
                                null,
                                RecordingStatus.COMPLETED,
                                Instant.parse("2026-04-01T10:00:00Z"),
                                Instant.parse("2026-04-01T11:00:00Z"),
                                3600,
                                1024L,
                                Instant.parse("2026-04-01T11:01:00Z"))),
                        List.of(new InviteeResponse(
                                userId,
                                "alice@example.com",
                                "Alice",
                                InviteeStatus.ACCEPTED,
                                Instant.parse("2026-03-31T08:00:00Z"),
                                Instant.parse("2026-03-31T09:00:00Z"))))));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings/{meetingId}", userId, meetingId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.data.participants.length()").value(1))
                .andExpect(jsonPath("$.data.recordings.length()").value(1))
                .andExpect(jsonPath("$.data.invitees.length()").value(1));
    }

    @Test
    void getParticipatedMeetingDetail_returns404WhenMeetingNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(getParticipatedMeetingDetailUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingDetailQuery(userId, meetingId, userId)))))
                .thenReturn(Result.failure(new MeetingError.MeetingNotFound(meetingId)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings/{meetingId}", userId, meetingId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void getParticipatedMeetingDetail_returns403ForNotParticipant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(getParticipatedMeetingDetailUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingDetailQuery(userId, meetingId, userId)))))
                .thenReturn(Result.failure(new MeetingError.NotParticipant(userId, meetingId)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings/{meetingId}", userId, meetingId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("NOT_PARTICIPANT"));
    }

    @Test
    void getParticipatedMeetingDetail_returns403ForWrongUserScope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(getParticipatedMeetingDetailUseCase.execute(argThat(query -> query.equals(
                        new GetParticipatedMeetingDetailQuery(userId, meetingId, requesterId)))))
                .thenReturn(Result.failure(new MeetingError.NotOwner(requesterId, userId)));

        mockMvc.perform(get("/api/v1/users/{userId}/meetings/{meetingId}", userId, meetingId)
                        .principal(new TestingAuthenticationToken(requesterId.toString(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("NOT_OWNER"));

        verify(getParticipatedMeetingDetailUseCase)
                .execute(new GetParticipatedMeetingDetailQuery(userId, meetingId, requesterId));
    }

    @Test
    void getParticipatedMeetingDetail_returns401WithoutPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/meetings/{meetingId}", userId, meetingId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
