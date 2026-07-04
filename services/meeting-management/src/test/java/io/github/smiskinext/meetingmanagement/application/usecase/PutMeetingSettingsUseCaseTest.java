package io.github.smiskinext.meetingmanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.application.command.PutMeetingSettingsCommand;
import io.github.smiskinext.meetingmanagement.application.helper.PendingJoinRequestApprover;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingInviteTokensInvalidatedEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingSettingsUpdatedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingInvitee;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingType;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviterId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.InviteTokenRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingLimitsPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.Email;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PutMeetingSettingsUseCaseTest {

    @Mock
    MeetingRepository meetingRepository;

    @Mock
    MeetingLimitsPort limitsConfig;

    @Mock
    PendingJoinRequestApprover pendingJoinRequestApprover;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    InviteTokenRepository inviteTokenRepository;

    @Mock
    MeetingInviteeRepository meetingInviteeRepository;

    @Mock
    ParticipationLogRepository participationLogRepository;

    PutMeetingSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PutMeetingSettingsUseCase(
                meetingRepository,
                limitsConfig,
                pendingJoinRequestApprover,
                eventPublisher,
                passwordHasher,
                inviteTokenRepository,
                meetingInviteeRepository,
                participationLogRepository);
        lenient().when(participationLogRepository.countActiveByMeetingId(any())).thenReturn(0L);
    }

    @Test
    void execute_replacesSettingsAndPublishesEvent() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.SCHEDULED,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        false, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        "old"));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordHasher.hash("secret-pass")).thenReturn("hashed-secret");
        when(meetingInviteeRepository.findByMeetingId(meetingId)).thenReturn(List.of());

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        true, // allowGuest
                        40, // maxParticipants
                        false, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                "secret-pass"));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (MeetingSettingsResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.allowGuest()).isTrue();
        assertThat(response.maxParticipants()).isEqualTo(40);
        assertThat(response.allowScreenShare()).isFalse();
        assertThat(response.chatEnabled()).isTrue();
        assertThat(response.allowMicrophone()).isTrue();
        assertThat(response.allowVideo()).isTrue();
        assertThat(response.requirePassword()).isTrue();

        verify(passwordHasher).hash("secret-pass");
        verify(meetingRepository)
                .save(argThat(
                        saved -> "hashed-secret".equals(saved.getSettings().password())
                                && saved.getSettings().allowGuest()
                                && saved.getSettings().maxParticipants() == 40
                                && !saved.getSettings().allowScreenShare()));

        ArgumentCaptor<Object> rawEventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(1)).publishEvent(rawEventCaptor.capture());
        MeetingSettingsUpdatedEvent settingsEvent = rawEventCaptor.getAllValues().stream()
                .filter(MeetingSettingsUpdatedEvent.class::isInstance)
                .map(MeetingSettingsUpdatedEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected MeetingSettingsUpdatedEvent"));
        assertThat(settingsEvent.aggregateId()).isEqualTo(meetingId);
        assertThat(settingsEvent.hostId()).isEqualTo(hostId);
        assertThat(settingsEvent.updatedBy()).isEqualTo(hostId);
        assertThat(settingsEvent.meetingStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(settingsEvent.oldSettings().allowGuest()).isFalse();
        assertThat(settingsEvent.oldSettings().maxParticipants()).isEqualTo(30);
        assertThat(settingsEvent.oldSettings().allowScreenShare()).isTrue();
        assertThat(settingsEvent.oldSettings().chatEnabled()).isFalse();
        assertThat(settingsEvent.newSettings().allowGuest()).isTrue();
        assertThat(settingsEvent.newSettings().maxParticipants()).isEqualTo(40);
        assertThat(settingsEvent.newSettings().allowScreenShare()).isFalse();
        assertThat(settingsEvent.newSettings().chatEnabled()).isTrue();
        verify(pendingJoinRequestApprover, never()).approveAll(any(), any());
    }

    @Test
    void execute_nullPassword_clearsStoredHash() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.SCHEDULED,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        "old"));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(meetingInviteeRepository.findByMeetingId(meetingId)).thenReturn(List.of());

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (MeetingSettingsResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.requirePassword()).isFalse();
        verify(meetingRepository).save(argThat(saved -> saved.getSettings().password() == null));
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void execute_returnsNotAuthorizedForNonHost() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.SCHEDULED,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false, // allowGuest
                                30, // maxParticipants
                                true, // allowScreenShare
                                true, // chatEnabled
                                true, // allowMicrophone
                                true, // allowVideo
                                null))));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                requesterId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.NotAuthorized(requesterId, hostId));
        verifyNoInteractions(passwordHasher, pendingJoinRequestApprover, eventPublisher);
    }

    @Test
    void execute_returnsInvalidStatusForEndedMeeting() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.ENDED,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false, // allowGuest
                                30, // maxParticipants
                                true, // allowScreenShare
                                true, // chatEnabled
                                true, // allowMicrophone
                                true, // allowVideo
                                null))));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.InvalidStatusTransition(
                        MeetingStatus.ENDED, MeetingStatus.SCHEDULED));
        verifyNoInteractions(passwordHasher, pendingJoinRequestApprover, eventPublisher);
    }

    @Test
    void execute_rejectsMaxParticipantsAboveCeiling() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(50);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.SCHEDULED,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false, // allowGuest
                                30, // maxParticipants
                                true, // allowScreenShare
                                true, // chatEnabled
                                true, // allowMicrophone
                                true, // allowVideo
                                null))));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        80, // maxParticipants - above ceiling
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isInstanceOf(MeetingError.InvalidSettings.class);
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(pendingJoinRequestApprover, eventPublisher);
    }

    @Test
    void execute_acceptsAllowAllWithMaxParticipantsChange() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.SCHEDULED,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false,
                                30,
                                true,
                                true,
                                true,
                                true,
                                null))));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.ALLOW_ALL, false, 40, true, true, true, true, null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(meetingRepository).save(any());
    }

    @Test
    void execute_rejectsMaxParticipantsBelowActiveCount() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(participationLogRepository.countActiveByMeetingId(meetingId)).thenReturn(5L);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.LIVE,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false,
                                10,
                                true,
                                true,
                                true,
                                true,
                                null))));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 3, true, true, true, true, null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.MeetingFull(meetingId, 3));
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void execute_acceptsMaxParticipantsEqualActiveCount() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(participationLogRepository.countActiveByMeetingId(meetingId)).thenReturn(5L);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.LIVE,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false,
                                10,
                                true,
                                true,
                                true,
                                true,
                                null))));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 5, true, true, true, true, null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(meetingRepository).save(any());
    }

    @Test
    void execute_acceptsZeroMaxParticipantsAsUnlimited() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.LIVE,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false,
                                10,
                                true,
                                true,
                                true,
                                true,
                                null))));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 0, true, true, true, true, null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(meetingRepository).save(any());
    }

    @Test
    void execute_livePolicyOpening_autoApprovesPendingRequests() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.LIVE,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pendingJoinRequestApprover.approveAll(meeting, hostId)).thenReturn(Result.success(1));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.ALLOW_ALL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(pendingJoinRequestApprover).approveAll(meeting, hostId);
    }

    @Test
    void execute_liveAllowGuestOpening_autoApprovesPendingRequests() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.LIVE,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest - starts false
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pendingJoinRequestApprover.approveAll(meeting, hostId)).thenReturn(Result.success(1));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        true, // allowGuest - changed to true
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);
        verify(pendingJoinRequestApprover).approveAll(meeting, hostId);
    }

    @Test
    void execute_liveAccessOpening_returnsFailureWhenAutoApprovalFails() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.LIVE,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(pendingJoinRequestApprover.approveAll(meeting, hostId))
                .thenReturn(Result.failure(
                        new MeetingError.LiveKitUnavailable("token generation failed")));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.ALLOW_ALL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.LiveKitUnavailable("token generation failed"));
        verify(meetingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void execute_returnsInvalidStatusForCancelledMeeting() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetingRepository.findByIdWithLock(meetingId))
                .thenReturn(Optional.of(meetingWithStatus(
                        meetingId,
                        hostId,
                        MeetingStatus.CANCELLED,
                        settings(
                                AdmissionPolicy.MANUAL_APPROVAL,
                                false, // allowGuest
                                30, // maxParticipants
                                true, // allowScreenShare
                                true, // chatEnabled
                                true, // allowMicrophone
                                true, // allowVideo
                                null))));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false, // allowGuest
                        30, // maxParticipants
                        true, // allowScreenShare
                        true, // chatEnabled
                        true, // allowMicrophone
                        true, // allowVideo
                        null),
                null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?, MeetingError>) result).error())
                .isEqualTo(new MeetingError.InvalidStatusTransition(
                        MeetingStatus.CANCELLED, MeetingStatus.SCHEDULED));
        verifyNoInteractions(passwordHasher, pendingJoinRequestApprover, eventPublisher);
    }

    @Test
    void execute_scheduledPasswordChange_revokesTokensAndPublishesInvalidationEvent() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        MeetingInvitee invitee = MeetingInvitee.create(
                MeetingId.of(meetingId),
                InviterId.of(hostId),
                UserId.of(inviteeUserId),
                Email.of("alice@example.com"),
                null);
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.SCHEDULED,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false,
                        30,
                        true,
                        true,
                        true,
                        true,
                        "old-hash"));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordHasher.hash("new-pass")).thenReturn("new-hash");
        when(inviteTokenRepository.revokeAllPendingByMeetingId(meetingId)).thenReturn(1);
        when(meetingInviteeRepository.findByMeetingId(meetingId)).thenReturn(List.of(invitee));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 30, true, true, true, true, null),
                "new-pass"));

        assertThat(result).isInstanceOf(Result.Success.class);
        var response = (MeetingSettingsResponse) ((Result.Success<?, ?>) result).value();
        assertThat(response.resendInvitesRecommended()).isTrue();

        verify(inviteTokenRepository).revokeAllPendingByMeetingId(meetingId);
        verify(meetingInviteeRepository).findByMeetingId(meetingId);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(1)).publishEvent(eventCaptor.capture());
        MeetingInviteTokensInvalidatedEvent invalidationEvent = eventCaptor.getAllValues().stream()
                .filter(MeetingInviteTokensInvalidatedEvent.class::isInstance)
                .map(MeetingInviteTokensInvalidatedEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected MeetingInviteTokensInvalidatedEvent to be published"));
        assertThat(invalidationEvent.aggregateId()).isEqualTo(meetingId);
        assertThat(invalidationEvent.hostId()).isEqualTo(hostId);
        assertThat(invalidationEvent.affectedInvitees()).hasSize(1);
        assertThat(invalidationEvent.affectedInvitees().getFirst().email())
                .isEqualTo("alice@example.com");
        assertThat(invalidationEvent.affectedInvitees().getFirst().userId())
                .isEqualTo(inviteeUserId);
    }

    @Test
    void execute_scheduledNoPasswordChange_doesNotRevokeTokens() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.SCHEDULED,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false,
                        30,
                        true,
                        true,
                        true,
                        true,
                        "same-hash"));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordHasher.hash("current-pass")).thenReturn("same-hash");

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, true, 30, true, true, true, true, null),
                "current-pass"));

        assertThat(result).isInstanceOf(Result.Success.class);
        verifyNoInteractions(inviteTokenRepository);
        verifyNoInteractions(meetingInviteeRepository);
    }

    @Test
    void execute_liveMeetingPasswordChange_doesNotRevokeTokens() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.LIVE,
                settings(
                        AdmissionPolicy.MANUAL_APPROVAL,
                        false,
                        30,
                        true,
                        true,
                        true,
                        true,
                        "old-hash"));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordHasher.hash("new-pass")).thenReturn("new-hash");

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 30, true, true, true, true, null),
                "new-pass"));

        assertThat(result).isInstanceOf(Result.Success.class);
        verifyNoInteractions(inviteTokenRepository);
        verifyNoInteractions(meetingInviteeRepository);
    }

    @Test
    void execute_liveMeetingScreenShareToggle_publishesSettingsUpdatedEvent() {
        UUID meetingId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Meeting meeting = meetingWithStatus(
                meetingId,
                hostId,
                MeetingStatus.LIVE,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 30, true, true, true, true, null));
        when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);
        when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new PutMeetingSettingsCommand(
                meetingId,
                hostId,
                settings(AdmissionPolicy.MANUAL_APPROVAL, false, 30, false, true, true, true, null),
                null));

        assertThat(result).isInstanceOf(Result.Success.class);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(1)).publishEvent(eventCaptor.capture());
        MeetingSettingsUpdatedEvent settingsEvent = eventCaptor.getAllValues().stream()
                .filter(MeetingSettingsUpdatedEvent.class::isInstance)
                .map(MeetingSettingsUpdatedEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected MeetingSettingsUpdatedEvent"));

        assertThat(settingsEvent.aggregateId()).isEqualTo(meetingId);
        assertThat(settingsEvent.hostId()).isEqualTo(hostId);
        assertThat(settingsEvent.updatedBy()).isEqualTo(hostId);
        assertThat(settingsEvent.meetingStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(settingsEvent.oldSettings().allowScreenShare()).isTrue();
        assertThat(settingsEvent.newSettings().allowScreenShare()).isFalse();
    }

    private static Meeting meetingWithStatus(
            UUID meetingId, UUID hostId, MeetingStatus status, MeetingSettings settings) {
        return Meeting.reconstitute(
                MeetingId.of(meetingId),
                UserId.of(hostId),
                ShortCode.of("ABC1234567"),
                null,
                null,
                null,
                null,
                MeetingType.INSTANT,
                status,
                settings,
                Instant.parse("2026-04-02T10:00:00Z"));
    }

    private static MeetingSettings settings(
            AdmissionPolicy admissionPolicy,
            boolean allowGuest,
            int maxParticipants,
            boolean allowScreenShare,
            boolean chatEnabled,
            boolean allowMicrophone,
            boolean allowVideo,
            String password) {
        return new MeetingSettings(
                admissionPolicy,
                allowGuest,
                maxParticipants,
                allowScreenShare,
                chatEnabled,
                allowMicrophone,
                allowVideo,
                password);
    }
}
