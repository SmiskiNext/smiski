package io.github.smiskinext.meetingmanagement.application.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingSettingsUpdatedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipationLog;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link MeetingSettingsChangedHandler}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Non-LIVE meetings are skipped</li>
 *   <li>Non-permission field changes are skipped</li>
 *   <li>HOST and GUEST sessions are skipped</li>
 *   <li>PARTICIPANT sessions are updated</li>
 *   <li>Best-effort processing continues after individual failures</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MeetingSettingsChangedHandlerTest {

    @Mock
    ParticipationLogRepository participationLogRepository;

    @Mock
    LiveKitPort liveKitPort;

    MeetingSettingsChangedHandler handler;

    UUID meetingId;
    UUID hostId;

    @BeforeEach
    void setUp() {
        handler = new MeetingSettingsChangedHandler(participationLogRepository, liveKitPort);
        meetingId = UUID.randomUUID();
        hostId = UUID.randomUUID();
    }

    @Test
    void handle_nonLiveMeeting_skipsWithoutRepositoryOrLiveKitWork() {
        var event = settingsEvent(MeetingStatus.SCHEDULED, allEnabled(), allDisabled());

        handler.handle(event);

        verifyNoInteractions(participationLogRepository, liveKitPort);
    }

    @Test
    void handle_endedMeeting_skipsWithoutRepositoryOrLiveKitWork() {
        var event = settingsEvent(MeetingStatus.ENDED, allEnabled(), allDisabled());

        handler.handle(event);

        verifyNoInteractions(participationLogRepository, liveKitPort);
    }

    @Test
    void handle_cancelledMeeting_skipsWithoutRepositoryOrLiveKitWork() {
        var event = settingsEvent(MeetingStatus.CANCELLED, allEnabled(), allDisabled());

        handler.handle(event);

        verifyNoInteractions(participationLogRepository, liveKitPort);
    }

    @Test
    void handle_noPermissionFieldsChanged_skipsWithoutRepositoryOrLiveKitWork() {
        MeetingSettings oldSettings = settingsWithPermissions(true, true, true, true);
        MeetingSettings newSettings = new MeetingSettings(
                AdmissionPolicy.ALLOW_ALL, // Changed, but not permission-related
                false, // Changed, but not permission-related
                50, // Changed, but not permission-related
                true, // unchanged
                true, // unchanged
                true, // unchanged
                true, // unchanged
                "secret"); // Changed, but not permission-related

        var event = settingsEvent(MeetingStatus.LIVE, oldSettings, newSettings);

        handler.handle(event);

        verifyNoInteractions(participationLogRepository, liveKitPort);
    }

    @Test
    void handle_microphoneChanged_triggersSync() {
        MeetingSettings oldSettings = settingsWithPermissions(true, true, true, true);
        MeetingSettings newSettings = settingsWithPermissions(false, true, true, true);

        var event = settingsEvent(MeetingStatus.LIVE, oldSettings, newSettings);
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        handler.handle(event);

        verify(participationLogRepository).findActiveByMeetingId(meetingId);
    }

    @Test
    void handle_videoChanged_triggersSync() {
        MeetingSettings oldSettings = settingsWithPermissions(true, true, true, true);
        MeetingSettings newSettings = settingsWithPermissions(true, false, true, true);

        var event = settingsEvent(MeetingStatus.LIVE, oldSettings, newSettings);
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        handler.handle(event);

        verify(participationLogRepository).findActiveByMeetingId(meetingId);
    }

    @Test
    void handle_screenShareChanged_triggersSync() {
        MeetingSettings oldSettings = settingsWithPermissions(true, true, true, true);
        MeetingSettings newSettings = settingsWithPermissions(true, true, false, true);

        var event = settingsEvent(MeetingStatus.LIVE, oldSettings, newSettings);
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        handler.handle(event);

        verify(participationLogRepository).findActiveByMeetingId(meetingId);
    }

    @Test
    void handle_chatEnabledChanged_triggersSync() {
        MeetingSettings oldSettings = settingsWithPermissions(true, true, true, true);
        MeetingSettings newSettings = settingsWithPermissions(true, true, true, false);

        var event = settingsEvent(MeetingStatus.LIVE, oldSettings, newSettings);
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        handler.handle(event);

        verify(participationLogRepository).findActiveByMeetingId(meetingId);
    }

    @Test
    void handle_skipsHostSessions() {
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), allDisabled());
        var hostSession = participationLog("host-identity", ParticipantRole.HOST);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(hostSession));

        handler.handle(event);

        verify(liveKitPort, never()).updateParticipantPermissions(any(), any(), any());
    }

    @Test
    void handle_skipsGuestSessions() {
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), allDisabled());
        var guestSession = participationLog("guest-identity", ParticipantRole.GUEST);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(guestSession));

        handler.handle(event);

        verify(liveKitPort, never()).updateParticipantPermissions(any(), any(), any());
    }

    @Test
    void handle_updatesParticipantSessions() {
        MeetingSettings newSettings = allDisabled();
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), newSettings);
        var participantSession =
                participationLog("participant-identity", ParticipantRole.PARTICIPANT);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(participantSession));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());

        handler.handle(event);

        ParticipantGrants expectedGrants = ParticipantGrants.fromSettingsForRuntimeSync(
                newSettings, ParticipantRole.PARTICIPANT);
        verify(liveKitPort)
                .updateParticipantPermissions(
                        eq(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))),
                        eq("participant-identity"),
                        eq(expectedGrants));
    }

    @Test
    void handle_mixedRoles_updatesOnlyParticipants() {
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), allDisabled());
        var hostSession = participationLog("host-identity", ParticipantRole.HOST);
        var participant1 = participationLog("participant1-identity", ParticipantRole.PARTICIPANT);
        var guestSession = participationLog("guest-identity", ParticipantRole.GUEST);
        var participant2 = participationLog("participant2-identity", ParticipantRole.PARTICIPANT);

        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(hostSession, participant1, guestSession, participant2));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());

        handler.handle(event);

        verify(liveKitPort, times(2)).updateParticipantPermissions(any(), any(), any());
        verify(liveKitPort).updateParticipantPermissions(any(), eq("participant1-identity"), any());
        verify(liveKitPort).updateParticipantPermissions(any(), eq("participant2-identity"), any());
    }

    @Test
    void handle_bestEffort_continuesAfterFailure() {
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), allDisabled());
        var participant1 = participationLog("participant1-identity", ParticipantRole.PARTICIPANT);
        var participant2 = participationLog("participant2-identity", ParticipantRole.PARTICIPANT);
        var participant3 = participationLog("participant3-identity", ParticipantRole.PARTICIPANT);

        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(participant1, participant2, participant3));
        when(liveKitPort.updateParticipantPermissions(any(), eq("participant1-identity"), any()))
                .thenReturn(Result.success());
        when(liveKitPort.updateParticipantPermissions(any(), eq("participant2-identity"), any()))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("network error")));
        when(liveKitPort.updateParticipantPermissions(any(), eq("participant3-identity"), any()))
                .thenReturn(Result.success());

        handler.handle(event);

        verify(liveKitPort).updateParticipantPermissions(any(), eq("participant1-identity"), any());
        verify(liveKitPort).updateParticipantPermissions(any(), eq("participant2-identity"), any());
        verify(liveKitPort).updateParticipantPermissions(any(), eq("participant3-identity"), any());
    }

    @Test
    void handle_noActiveSessions_completesWithoutLiveKitCalls() {
        var event = settingsEvent(MeetingStatus.LIVE, allEnabled(), allDisabled());
        when(participationLogRepository.findActiveByMeetingId(meetingId)).thenReturn(List.of());

        handler.handle(event);

        verify(participationLogRepository).findActiveByMeetingId(meetingId);
        verifyNoInteractions(liveKitPort);
    }

    @Test
    void handle_screenShareRevoked_mutesParticipantScreenShareTracks() {
        var event = settingsEvent(
                MeetingStatus.LIVE, allEnabled(), settingsWithPermissions(true, true, false, true));
        var hostSession = participationLog("host-identity", ParticipantRole.HOST);
        var participant = participationLog("participant-identity", ParticipantRole.PARTICIPANT);
        var guestSession = participationLog("guest-identity", ParticipantRole.GUEST);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(hostSession, participant, guestSession));
        when(liveKitPort.updateParticipantPermissions(any(), eq("participant-identity"), any()))
                .thenReturn(Result.success());
        when(liveKitPort.muteParticipantTrack(
                        any(), eq("participant-identity"), eq("screen_share")))
                .thenReturn(Result.success());

        handler.handle(event);

        verify(liveKitPort)
                .muteParticipantTrack(
                        eq(LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId))),
                        eq("participant-identity"),
                        eq("screen_share"));
        verify(liveKitPort, never()).muteParticipantTrack(any(), eq("host-identity"), any());
        verify(liveKitPort, never()).muteParticipantTrack(any(), eq("guest-identity"), any());
    }

    @Test
    void handle_screenShareMuteTrackNotFound_continuesToNextParticipant() {
        var event = settingsEvent(
                MeetingStatus.LIVE, allEnabled(), settingsWithPermissions(true, true, false, true));
        var participant1 = participationLog("participant1-identity", ParticipantRole.PARTICIPANT);
        var participant2 = participationLog("participant2-identity", ParticipantRole.PARTICIPANT);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(participant1, participant2));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());
        when(liveKitPort.muteParticipantTrack(
                        any(), eq("participant1-identity"), eq("screen_share")))
                .thenReturn(Result.failure(
                        new MeetingError.TrackNotFound("participant1-identity", "screen_share")));
        when(liveKitPort.muteParticipantTrack(
                        any(), eq("participant2-identity"), eq("screen_share")))
                .thenReturn(Result.success());

        handler.handle(event);

        verify(liveKitPort)
                .muteParticipantTrack(any(), eq("participant1-identity"), eq("screen_share"));
        verify(liveKitPort)
                .muteParticipantTrack(any(), eq("participant2-identity"), eq("screen_share"));
    }

    @Test
    void handle_screenShareMuteParticipantNotFound_continuesToNextParticipant() {
        var event = settingsEvent(
                MeetingStatus.LIVE, allEnabled(), settingsWithPermissions(true, true, false, true));
        var participant1 = participationLog("participant1-identity", ParticipantRole.PARTICIPANT);
        var participant2 = participationLog("participant2-identity", ParticipantRole.PARTICIPANT);
        when(participationLogRepository.findActiveByMeetingId(meetingId))
                .thenReturn(List.of(participant1, participant2));
        when(liveKitPort.updateParticipantPermissions(any(), any(), any()))
                .thenReturn(Result.success());
        when(liveKitPort.muteParticipantTrack(
                        any(), eq("participant1-identity"), eq("screen_share")))
                .thenReturn(Result.failure(new MeetingError.LiveKitParticipantNotFound(
                        LiveKitRoomName.fromMeetingId(MeetingId.of(meetingId)).value(),
                        "participant1-identity")));
        when(liveKitPort.muteParticipantTrack(
                        any(), eq("participant2-identity"), eq("screen_share")))
                .thenReturn(Result.success());

        handler.handle(event);

        verify(liveKitPort)
                .muteParticipantTrack(any(), eq("participant1-identity"), eq("screen_share"));
        verify(liveKitPort)
                .muteParticipantTrack(any(), eq("participant2-identity"), eq("screen_share"));
    }

    private MeetingSettingsUpdatedEvent settingsEvent(
            MeetingStatus status, MeetingSettings oldSettings, MeetingSettings newSettings) {
        return new MeetingSettingsUpdatedEvent(
                UUID.randomUUID(),
                meetingId,
                hostId,
                hostId,
                status,
                oldSettings,
                newSettings,
                Instant.now());
    }

    private MeetingSettings allEnabled() {
        return settingsWithPermissions(true, true, true, true);
    }

    private MeetingSettings allDisabled() {
        return settingsWithPermissions(false, false, false, false);
    }

    private MeetingSettings settingsWithPermissions(
            boolean allowMicrophone,
            boolean allowVideo,
            boolean allowScreenShare,
            boolean chatEnabled) {
        return new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL,
                true,
                100,
                allowScreenShare,
                chatEnabled,
                allowMicrophone,
                allowVideo,
                null);
    }

    private ParticipationLog participationLog(String identity, ParticipantRole role) {
        return ParticipationLog.reconstitute(
                null,
                MeetingId.of(meetingId),
                role == ParticipantRole.GUEST ? null : UUID.randomUUID(),
                "Test User",
                role,
                LiveKitIdentity.of(identity),
                null,
                Instant.now(),
                null);
    }
}
