package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.helper.ShortCodeGenerator;
import io.github.smiskinext.meetingmanagement.application.service.InviteTokenService;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInvitationsSentEvent;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingLimitsPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort.ResolvedUser;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ScheduleMeetingUseCaseEventPublishingTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MeetingLimitsPort meetingLimitsPort;

    @Mock
    private UserGrpcServicePort userGrpcServicePort;

    @Mock
    private MeetingInviteeRepository inviteeRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private InviteTokenService inviteTokenService;

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    private ScheduleMeetingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ScheduleMeetingUseCase(
                meetingRepository,
                shortCodeGenerator,
                eventPublisher,
                meetingLimitsPort,
                userGrpcServicePort,
                inviteeRepository,
                passwordHasher,
                inviteTokenService,
                inviteTokenRepository);
        when(meetingLimitsPort.getMinDurationMinutes()).thenReturn(15);
        when(meetingLimitsPort.getMaxDurationMinutes()).thenReturn(240);
        when(meetingLimitsPort.getMaxParticipantsCeiling()).thenReturn(300);
        when(shortCodeGenerator.generate()).thenReturn(Result.success(ShortCode.of("SHORT12345")));
        when(meetingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(inviteeRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(inviteTokenService.generateToken(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("raw-token");
        lenient().when(inviteTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        lenient()
                .when(inviteTokenService.extractExpiresAt("raw-token"))
                .thenReturn(Instant.parse("2027-04-10T10:00:00Z"));
        lenient()
                .when(inviteTokenRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesInvitationEventWithShortCodeAndInvitees() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        when(passwordHasher.hash("secret-pass")).thenReturn("hashed-secret");
        when(userGrpcServicePort.resolveUsers(List.of("alice@example.com")))
                .thenReturn(Map.of(
                        "alice@example.com",
                        new ResolvedUser(
                                inviteeId, "alice@example.com", "Alice", null, null, "EMAIL")));

        var result = useCase.execute(command(
                hostId,
                MeetingTitle.of("Weekly Sync"),
                List.of(new ScheduleMeetingCommand.InviteeInput("alice@example.com")),
                "secret-pass"));

        assertThat(result).isInstanceOf(Result.Success.class);

        MeetingInvitationsSentEvent event = capturedInvitationEvent();
        assertThat(event.meetingTitle()).isEqualTo("Weekly Sync");
        assertThat(event.meetingShortCode()).isEqualTo("SHORT12345");
        assertThat(event.invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.userId()).isEqualTo(inviteeId);
            assertThat(invitee.email()).isEqualTo("alice@example.com");
            assertThat(invitee.displayName()).isEqualTo("Alice");
        });
    }

    @Test
    void publishesInvitationEventWithoutPasswordInPayload() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        when(userGrpcServicePort.resolveUsers(List.of("guest@example.com")))
                .thenReturn(Map.of(
                        "guest@example.com",
                        new ResolvedUser(
                                inviteeId,
                                "guest@example.com",
                                "Guest User",
                                null,
                                null,
                                "EMAIL")));

        var result = useCase.execute(command(
                hostId,
                null,
                List.of(new ScheduleMeetingCommand.InviteeInput("guest@example.com")),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);

        MeetingInvitationsSentEvent event = capturedInvitationEvent();
        assertThat(event.meetingTitle()).isNull();
        assertThat(event.meetingShortCode()).isEqualTo("SHORT12345");
        assertThat(event.startTime()).isEqualTo(Instant.parse("2026-04-03T10:00:00Z"));
    }

    @Test
    void doesNotPublishInvitationEventWhenInviteeListIsEmpty() {
        UUID hostId = UUID.randomUUID();

        var result =
                useCase.execute(command(hostId, MeetingTitle.of("Solo meeting"), List.of(), null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verifyNoInteractions(userGrpcServicePort, inviteeRepository, passwordHasher);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .noneMatch(MeetingInvitationsSentEvent.class::isInstance);
    }

    @Test
    void treatsBlankPasswordAsUnprotected() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        when(userGrpcServicePort.resolveUsers(List.of("bob@example.com")))
                .thenReturn(Map.of(
                        "bob@example.com",
                        new ResolvedUser(
                                inviteeId, "bob@example.com", "Bob", null, null, "EMAIL")));

        var result = useCase.execute(command(
                hostId,
                MeetingTitle.of("Whitespace password"),
                List.of(new ScheduleMeetingCommand.InviteeInput("bob@example.com")),
                "   "));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void generatesTokenPerInviteeAndIncludesInEvent() {
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String rawToken = "sig|" + UUID.randomUUID() + "|" + inviteeUserId + "|9999999999";
        when(userGrpcServicePort.resolveUsers(List.of("carol@example.com")))
                .thenReturn(Map.of(
                        "carol@example.com",
                        new ResolvedUser(
                                inviteeUserId, "carol@example.com", "Carol", null, null, "EMAIL")));
        when(inviteTokenService.generateToken(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(rawToken);
        when(inviteTokenService.hashToken(rawToken)).thenReturn("hashed-token");
        when(inviteTokenService.extractExpiresAt(rawToken))
                .thenReturn(Instant.parse("2027-04-10T10:00:00Z"));
        when(inviteTokenRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(inviteeRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(command(
                hostId,
                MeetingTitle.of("Token Meeting"),
                List.of(new ScheduleMeetingCommand.InviteeInput("carol@example.com")),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        MeetingInvitationsSentEvent event = eventCaptor.getAllValues().stream()
                .filter(MeetingInvitationsSentEvent.class::isInstance)
                .map(MeetingInvitationsSentEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected invitation event to be published"));

        assertThat(event.inviteeTokens()).isNotEmpty();
        assertThat(event.inviteeTokens()).containsKey(inviteeUserId);
        assertThat(event.inviteeTokens()).containsValue(rawToken);
        assertThat(event.inviteeTokens().get(inviteeUserId)).isEqualTo(rawToken);
        verify(inviteTokenRepository).save(org.mockito.ArgumentMatchers.any());
    }

    private MeetingInvitationsSentEvent capturedInvitationEvent() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        return eventCaptor.getAllValues().stream()
                .filter(MeetingInvitationsSentEvent.class::isInstance)
                .map(MeetingInvitationsSentEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected invitation event to be published"));
    }

    private ScheduleMeetingCommand command(
            UUID hostId,
            MeetingTitle title,
            List<ScheduleMeetingCommand.InviteeInput> invitees,
            String rawPassword) {
        return new ScheduleMeetingCommand(
                hostId,
                title,
                "Discuss roadmap",
                MeetingTimeRange.of(
                        Instant.parse("2026-04-03T10:00:00Z"),
                        Instant.parse("2026-04-03T11:00:00Z")),
                MeetingSettings.defaults(),
                invitees,
                rawPassword);
    }
}
