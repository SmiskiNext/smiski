package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.helper.ShortCodeAllocator;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.application.service.CreateInstantMeetingApplicationService;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.*;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateInstantMeetingApplicationServiceTest {

    private MeetingRepository meetingRepository;
    private MeetingInviteeRepository meetingInviteeRepository;
    private EventPublisher eventPublisher;
    private LiveKitPort liveKitPort;
    private ShortCodeAllocator shortCodeAllocator;
    private CreateInstantMeetingApplicationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        meetingInviteeRepository = mock(MeetingInviteeRepository.class);
        eventPublisher = mock(EventPublisher.class);
        liveKitPort = mock(LiveKitPort.class);
        shortCodeAllocator = mock(ShortCodeAllocator.class);

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meetingInviteeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("mock-livekit-token"));
        when(shortCodeAllocator.allocate(any())).thenAnswer(inv -> {
            Function<ShortCode, Object> action = inv.getArgument(0);
            return Optional.of(action.apply(ShortCode.of("abc123def0")));
        });

        service = new CreateInstantMeetingApplicationService(
                meetingRepository,
                meetingInviteeRepository,
                eventPublisher,
                liveKitPort,
                shortCodeAllocator,
                () -> Duration.ofHours(1));
    }

    @Test
    void successfulCreationYieldsLiveMeetingAndEvents() {
        CreateInstantMeetingCommand command = new CreateInstantMeetingCommand(
                "tenant-1",
                "Sprint Planning",
                "Daily standup for team",
                new CreateInstantMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new CreateInstantMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                new CreateInstantMeetingCommand.Host(
                        "host-account", "Alice", "device-1", "https://cdn.example.com/alice.png"),
                "alice@example.com",
                "Alice Nguyen",
                "Asia/Ho_Chi_Minh",
                List.of(
                        new CreateInstantMeetingCommand.Invitee(
                                "bob@test.com", "bob-account", "Bob"),
                        new CreateInstantMeetingCommand.Invitee(
                                "carol@test.com", "carol-account", "Carol")));

        Result<CreateInstantMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isSuccess()).isTrue();
        CreateInstantMeetingResult value =
                ((Result.Success<CreateInstantMeetingResult, MeetingError>) result).value();
        assertThat(value.type()).isEqualTo("INSTANT");
        assertThat(value.status()).isEqualTo("RUNNING");
        assertThat(value.hostId()).isEqualTo("host-account");
        assertThat(value.livekit().token()).isEqualTo("mock-livekit-token");
        assertThat(value.livekit().roomName()).startsWith("meeting-");
        assertThat(value.title()).isEqualTo("Sprint Planning");
        assertThat(value.description()).isEqualTo("Daily standup for team");
        assertThat(value.organizerEmail()).isEqualTo("alice@example.com");
        assertThat(value.organizerDisplayName()).isEqualTo("Alice Nguyen");
        assertThat(value.issueLink()).isNotNull();
        assertThat(value.issueLink().issueId()).isEqualTo("ISS-1");

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(meetingCaptor.capture());
        assertThat(meetingCaptor.getValue().getOrganizerEmail().value())
                .isEqualTo("alice@example.com");
        assertThat(meetingCaptor.getValue().getOrganizerDisplayName().value())
                .isEqualTo("Alice Nguyen");
        assertThat(meetingCaptor.getValue().getStartTime()).isPresent();
        assertThat(meetingCaptor.getValue().getEndTime()).isPresent();
        assertThat(meetingCaptor.getValue().getStartTime().get())
                .isBefore(meetingCaptor.getValue().getEndTime().get());
        verify(meetingInviteeRepository).saveAll(argThat(list -> list.size() == 2));

        ArgumentCaptor<LiveKitTokenRequest> tokenRequestCaptor =
                ArgumentCaptor.forClass(LiveKitTokenRequest.class);
        verify(liveKitPort).generateToken(tokenRequestCaptor.capture());
        assertThat(tokenRequestCaptor.getValue().participantAttributes().avatarUrl())
                .isEqualTo("https://cdn.example.com/alice.png");

        verify(eventPublisher).publishEventsOf(any(AggregateRoot.class));
    }

    @Test
    void shortCodeExhaustionYieldsShortCodeExhaustedError() {
        doReturn(Optional.empty()).when(shortCodeAllocator).allocate(any());

        CreateInstantMeetingCommand command = new CreateInstantMeetingCommand(
                "tenant-1",
                "Standup",
                "Quick sync",
                new CreateInstantMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new CreateInstantMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                new CreateInstantMeetingCommand.Host("host-account", "Alice", "device-1", null),
                "alice@example.com",
                "Alice Nguyen",
                "UTC",
                List.of());

        Result<CreateInstantMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        MeetingError error =
                ((Result.Failure<CreateInstantMeetingResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.ShortCodeExhausted.class);
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void liveKitFailureReturnsLiveKitUnavailableAndNothingIsPersisted() {
        when(liveKitPort.generateToken(any()))
                .thenReturn(
                        Result.failure(new MeetingError.LiveKitUnavailable("connection refused")));

        CreateInstantMeetingCommand command = new CreateInstantMeetingCommand(
                "tenant-1",
                "Standup",
                "Quick sync",
                new CreateInstantMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new CreateInstantMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                new CreateInstantMeetingCommand.Host("host-account", "Alice", "device-1", null),
                "alice@example.com",
                "Alice Nguyen",
                "UTC",
                List.of());

        Result<CreateInstantMeetingResult, MeetingError> result = service.execute(command);

        assertThat(result.isFailure()).isTrue();
        MeetingError error =
                ((Result.Failure<CreateInstantMeetingResult, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.LiveKitUnavailable.class);

        verify(meetingRepository, never()).save(any(Meeting.class));
        verify(meetingInviteeRepository, never()).saveAll(anyList());
        verify(eventPublisher, never()).publishEventsOf(any(AggregateRoot.class));
    }

    @Test
    void configuredDurationIsApplied() {
        Duration configuredDuration = Duration.ofMinutes(30);
        CreateInstantMeetingApplicationService customService =
                new CreateInstantMeetingApplicationService(
                        meetingRepository,
                        meetingInviteeRepository,
                        eventPublisher,
                        liveKitPort,
                        shortCodeAllocator,
                        () -> configuredDuration);

        CreateInstantMeetingCommand command = new CreateInstantMeetingCommand(
                "tenant-1",
                "Quick Sync",
                "30-min meeting",
                new CreateInstantMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new CreateInstantMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                new CreateInstantMeetingCommand.Host("host-account", "Alice", "device-1", null),
                "alice@example.com",
                "Alice Nguyen",
                "UTC",
                List.of());

        Result<CreateInstantMeetingResult, MeetingError> result = customService.execute(command);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(captor.capture());
        Meeting savedMeeting = captor.getValue();
        assertThat(savedMeeting.getTimeRange()).isPresent();
        assertThat(savedMeeting.getTimeRange().get().duration()).isEqualTo(configuredDuration);
    }

    @Test
    void defaultDurationIsAppliedWhenNotConfigured() {
        CreateInstantMeetingApplicationService defaultService =
                new CreateInstantMeetingApplicationService(
                        meetingRepository,
                        meetingInviteeRepository,
                        eventPublisher,
                        liveKitPort,
                        shortCodeAllocator,
                        () -> Duration.ofHours(1));

        CreateInstantMeetingCommand command = new CreateInstantMeetingCommand(
                "tenant-1",
                "Standup",
                "Daily",
                new CreateInstantMeetingCommand.IssueLink("ISS-1", "PROJ-1", "PROJ"),
                new CreateInstantMeetingCommand.Settings("ALLOW_ALL", 50, true, true, true, true),
                new CreateInstantMeetingCommand.Host("host-account", "Alice", "device-1", null),
                "alice@example.com",
                "Alice Nguyen",
                "UTC",
                List.of());

        Result<CreateInstantMeetingResult, MeetingError> result = defaultService.execute(command);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository, atLeastOnce()).save(captor.capture());
        Meeting savedMeeting = captor.getValue();
        assertThat(savedMeeting.getTimeRange()).isPresent();
        assertThat(savedMeeting.getStartTime().get())
                .isBefore(savedMeeting.getEndTime().get());
        assertThat(savedMeeting.getTimeRange().get().duration()).isEqualTo(Duration.ofHours(1));
    }
}
