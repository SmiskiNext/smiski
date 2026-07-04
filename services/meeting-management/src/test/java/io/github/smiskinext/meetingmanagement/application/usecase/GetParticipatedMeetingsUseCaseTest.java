package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.query.GetParticipatedMeetingsQuery;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.MeetingSettingsSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipatedMeetingSummary;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.phunguy65.zms.shared.domain.Result;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.github.smiskinext.meetingmanagement.application.response.ParticipatedMeetingPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetParticipatedMeetingsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    private GetParticipatedMeetingsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetParticipatedMeetingsUseCase(meetingRepository);
    }

    @Test
    void execute_rejectsRequesterOutsideUserScope() {
        UUID userId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        var result = useCase.execute(
                new GetParticipatedMeetingsQuery(userId, requesterId, Set.of(), 20, null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.NotOwner(requesterId, userId));
        verifyNoInteractions(meetingRepository);
    }

    @Test
    void execute_mapsParticipatedSummariesToPageResponse() {
        UUID userId = UUID.randomUUID();
        Instant lastJoinedAt = Instant.parse("2026-04-03T09:00:00Z");
        ParticipatedMeetingSummary summary = new ParticipatedMeetingSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ABC123",
                "Retro",
                "Weekly sync",
                Instant.parse("2026-04-03T10:00:00Z"),
                Instant.parse("2026-04-03T11:00:00Z"),
                MeetingType.SCHEDULED,
                MeetingStatus.ENDED,
                new MeetingSettingsSummary("ALLOW_ALL", true, 100, true, true, true, true, false),
                Instant.parse("2026-04-01T08:00:00Z"),
                lastJoinedAt);
        when(meetingRepository.findParticipatedSummariesByUserId(
                        userId, Set.of(MeetingStatus.ENDED), null, 20))
                .thenReturn(CursorPageResponse.of(java.util.List.of(summary), 20, false));

        var result = useCase.execute(new GetParticipatedMeetingsQuery(
                userId, userId, Set.of(MeetingStatus.ENDED), 20, null));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<
                ParticipatedMeetingPageResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.lastJoinedAt()).isEqualTo(lastJoinedAt);
            assertThat(item.meeting().id()).isEqualTo(summary.id());
            assertThat(item.meeting().status()).isEqualTo(MeetingStatus.ENDED);
            assertThat(item.meeting().settings().allowScreenShare()).isTrue();
        });
        verify(meetingRepository)
                .findParticipatedSummariesByUserId(userId, Set.of(MeetingStatus.ENDED), null, 20);
    }

    @Test
    void execute_normalizesPageSizeLessThanOne() {
        UUID userId = UUID.randomUUID();
        when(meetingRepository.findParticipatedSummariesByUserId(userId, Set.of(), null, 1))
                .thenReturn(CursorPageResponse.empty(1));

        var result = useCase.execute(
                new GetParticipatedMeetingsQuery(userId, userId, Set.of(), 0, null));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<
                ParticipatedMeetingPageResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(page.pageSize()).isEqualTo(1);
        verify(meetingRepository).findParticipatedSummariesByUserId(userId, Set.of(), null, 1);
    }

    @Test
    void execute_capsPageSizeAtHundred() {
        UUID userId = UUID.randomUUID();
        when(meetingRepository.findParticipatedSummariesByUserId(userId, Set.of(), null, 100))
                .thenReturn(CursorPageResponse.empty(100));

        var result = useCase.execute(
                new GetParticipatedMeetingsQuery(userId, userId, Set.of(), 200, null));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<
                ParticipatedMeetingPageResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(page.pageSize()).isEqualTo(100);
        verify(meetingRepository).findParticipatedSummariesByUserId(userId, Set.of(), null, 100);
    }

    @Test
    void execute_returnsEmptyPageWhenNoMeetingsExist() {
        UUID userId = UUID.randomUUID();
        when(meetingRepository.findParticipatedSummariesByUserId(userId, Set.of(), null, 20))
                .thenReturn(CursorPageResponse.empty(20));

        var result = useCase.execute(
                new GetParticipatedMeetingsQuery(userId, userId, Set.of(), 20, null));

        assertThat(result).isInstanceOf(Result.Success.class);
        var page = ((Result.Success<
                ParticipatedMeetingPageResponse,
                                MeetingError>)
                        result)
                .value();
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}
