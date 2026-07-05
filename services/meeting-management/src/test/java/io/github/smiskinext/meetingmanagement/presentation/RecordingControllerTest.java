package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meetingmanagement.application.query.GetMeetingRecordingsQuery;
import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.application.usecase.CompleteRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetMeetingRecordingsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.GetRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.StartRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.StopRecordingUseCase;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.infrastructure.web.WebConfig;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecordingController.class)
@Import(WebConfig.class)
class RecordingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StartRecordingUseCase startRecordingUseCase;

    @MockitoBean
    StopRecordingUseCase stopRecordingUseCase;

    @MockitoBean
    CompleteRecordingUseCase completeRecordingUseCase;

    @MockitoBean
    GetRecordingUseCase getRecordingUseCase;

    @MockitoBean
    GetMeetingRecordingsUseCase getMeetingRecordingsUseCase;

    @MockitoBean
    CursorTokenEncoder cursorTokenEncoder;

    @Test
    void listMeetingRecordings_returnsCursorEnvelopeAndNextPageToken() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID firstRecordingId = UUID.randomUUID();
        UUID secondRecordingId = UUID.randomUUID();
        Instant secondCreatedAt = Instant.parse("2026-04-01T09:59:00Z");
        when(getMeetingRecordingsUseCase.execute(
                        argThat(query -> query.meetingId().equals(meetingId)
                                && query.pageSize() == 5
                                && query.cursor() == null)))
                .thenReturn(CursorPageResponse.of(
                        List.of(
                                new RecordingResponse(
                                        firstRecordingId,
                                        meetingId,
                                        "https://cdn.example/video.mp4",
                                        "https://cdn.example/thumb.jpg",
                                        RecordingStatus.COMPLETED,
                                        Instant.parse("2026-04-01T10:00:00Z"),
                                        Instant.parse("2026-04-01T10:30:00Z"),
                                        1800,
                                        2048L,
                                        Instant.parse("2026-04-01T09:00:00Z")),
                                new RecordingResponse(
                                        secondRecordingId,
                                        meetingId,
                                        null,
                                        null,
                                        RecordingStatus.PENDING,
                                        Instant.parse("2026-04-01T11:00:00Z"),
                                        null,
                                        0,
                                        0L,
                                        secondCreatedAt)),
                        5,
                        true));
        when(cursorTokenEncoder.encode(secondCreatedAt, secondRecordingId))
                .thenReturn("next-token");

        mockMvc.perform(get("/api/v1/meetings/{id}/recordings", meetingId).param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(firstRecordingId.toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(secondRecordingId.toString()))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.nextPageToken").value("next-token"));

        verify(cursorTokenEncoder).encode(secondCreatedAt, secondRecordingId);
    }

    @Test
    void listMeetingRecordings_decodesPageTokenBeforeCallingUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        ScrollCursor cursor =
                new ScrollCursor(Instant.parse("2026-04-01T08:00:00Z"), UUID.randomUUID());
        when(cursorTokenEncoder.decode("valid-token")).thenReturn(Result.success(cursor));
        when(getMeetingRecordingsUseCase.execute(argThat(query ->
                        query.equals(new GetMeetingRecordingsQuery(meetingId, 7, cursor)))))
                .thenReturn(CursorPageResponse.of(
                        List.of(new RecordingResponse(
                                recordingId,
                                meetingId,
                                null,
                                null,
                                RecordingStatus.PENDING,
                                Instant.parse("2026-04-01T11:00:00Z"),
                                null,
                                0,
                                0L,
                                Instant.parse("2026-04-01T10:59:00Z"))),
                        7,
                        false));

        mockMvc.perform(get("/api/v1/meetings/{id}/recordings", meetingId)
                        .param("pageSize", "7")
                        .param("pageToken", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(7))
                .andExpect(jsonPath("$.data.nextPageToken").doesNotExist());

        verify(cursorTokenEncoder).decode("valid-token");
    }

    @Test
    void listMeetingRecordings_returns400ForInvalidPageToken() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(cursorTokenEncoder.decode("bad-token"))
                .thenReturn(Result.failure(CursorErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/meetings/{id}/recordings", meetingId)
                        .param("pageToken", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.data.code").value("INVALID_CURSOR"));

        verifyNoInteractions(getMeetingRecordingsUseCase);
    }
}
