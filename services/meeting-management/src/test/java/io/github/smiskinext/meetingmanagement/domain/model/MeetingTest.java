package io.github.smiskinext.meetingmanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingCancelledEvent;
import io.github.smiskinext.meetingmanagement.domain.event.MeetingSettingsUpdatedEvent;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTimeRange;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingTest {

    @Test
    void updateSettings_capturesOldAndNewSettingsInEvent() {
        Meeting meeting = scheduledMeeting();
        UUID updaterId = UUID.randomUUID();

        MeetingSettings oldSettings = meeting.getSettings();
        MeetingSettings newSettings = new MeetingSettings(
                AdmissionPolicy.ALLOW_ALL,
                false, // allowGuest
                50, // maxParticipants
                false, // allowScreenShare
                false, // chatEnabled
                false, // allowMicrophone
                true, // allowVideo
                null);

        Result<Void, MeetingError> result = meeting.updateSettings(newSettings, updaterId);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(meeting.getSettings()).isEqualTo(newSettings);
        assertThat(meeting.getDomainEvents())
                .last()
                .isInstanceOfSatisfying(MeetingSettingsUpdatedEvent.class, event -> {
                    assertThat(event.oldSettings()).isEqualTo(oldSettings);
                    assertThat(event.newSettings()).isEqualTo(newSettings);
                    assertThat(event.updatedBy()).isEqualTo(updaterId);
                    assertThat(event.meetingStatus()).isEqualTo(MeetingStatus.SCHEDULED);
                });
    }

    @Test
    void updateSettings_forLiveMeeting_includesLiveStatusInEvent() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.start()).isInstanceOf(Result.Success.class);
        meeting.clearDomainEvents(); // Clear start event to isolate settings update

        MeetingSettings newSettings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL,
                true,
                100,
                false, // allowScreenShare changed
                false, // chatEnabled changed
                true,
                true,
                null);

        Result<Void, MeetingError> result = meeting.updateSettings(newSettings, UUID.randomUUID());

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(meeting.getDomainEvents())
                .last()
                .isInstanceOfSatisfying(MeetingSettingsUpdatedEvent.class, event -> {
                    assertThat(event.meetingStatus()).isEqualTo(MeetingStatus.LIVE);
                    assertThat(event.oldSettings().allowScreenShare()).isTrue();
                    assertThat(event.newSettings().allowScreenShare()).isFalse();
                });
    }

    @Test
    void updateSettings_forEndedMeeting_fails() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.start()).isInstanceOf(Result.Success.class);
        assertThat(meeting.end()).isInstanceOf(Result.Success.class);

        Result<Void, MeetingError> result =
                meeting.updateSettings(MeetingSettings.defaults(), UUID.randomUUID());

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.ENDED, MeetingStatus.SCHEDULED)));
    }

    @Test
    void cancelScheduledMeeting_registersCancelledEventWithInvitees() {
        Meeting meeting = scheduledMeeting();

        Result<Void, MeetingError> result = meeting.cancel(
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingCancelledEvent.InviteeInfo(
                        UUID.randomUUID(),
                        "alice@example.com",
                        "Alice",
                        "PENDING",
                        Instant.parse("2026-04-02T09:00:00Z"))));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
        assertThat(meeting.getDomainEvents())
                .last()
                .isInstanceOfSatisfying(MeetingCancelledEvent.class, event -> {
                    assertThat(event.meetingTitle()).isEqualTo("Planning Session");
                    assertThat(event.meetingShortCode()).isEqualTo("ABC1234567");
                    assertThat(event.invitees()).hasSize(1);
                });
    }

    @Test
    void cancelScheduledMeeting_withoutInvitees_registersEmptyInviteeList() {
        Meeting meeting = scheduledMeeting();

        Result<Void, MeetingError> result = meeting.cancel();

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(meeting.getDomainEvents())
                .last()
                .isInstanceOfSatisfying(
                        MeetingCancelledEvent.class,
                        event -> assertThat(event.invitees()).isEmpty());
    }

    @Test
    void cancelLiveMeeting_fails() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.start()).isInstanceOf(Result.Success.class);

        Result<Void, MeetingError> result = meeting.cancel();

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.LIVE, MeetingStatus.CANCELLED)));
    }

    @Test
    void cancelEndedMeeting_fails() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.start()).isInstanceOf(Result.Success.class);
        assertThat(meeting.end()).isInstanceOf(Result.Success.class);

        Result<Void, MeetingError> result = meeting.cancel();

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.ENDED, MeetingStatus.CANCELLED)));
    }

    @Test
    void cancelCancelledMeeting_fails() {
        Meeting meeting = scheduledMeeting();
        assertThat(meeting.cancel()).isInstanceOf(Result.Success.class);

        Result<Void, MeetingError> result = meeting.cancel();

        assertThat(result)
                .isInstanceOfSatisfying(Result.Failure.class, failure -> assertThat(failure.error())
                        .isEqualTo(new MeetingError.InvalidStatusTransition(
                                MeetingStatus.CANCELLED, MeetingStatus.CANCELLED)));
    }

    @Test
    void cancelInstantMeeting_isAllowedWhileStillScheduled() {
        Meeting meeting = Meeting.instant(
                UserId.of(UUID.randomUUID()),
                MeetingTitle.of("Instant"),
                null,
                MeetingSettings.defaults(),
                ShortCode.of("ABC1234567"));

        Result<Void, MeetingError> result = meeting.cancel();

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
    }

    private static Meeting scheduledMeeting() {
        return Meeting.schedule(
                UserId.of(UUID.randomUUID()),
                MeetingTitle.of("Planning Session"),
                "Discuss roadmap",
                MeetingTimeRange.of(
                        Instant.parse("2026-04-03T10:00:00Z"),
                        Instant.parse("2026-04-03T11:00:00Z")),
                MeetingSettings.defaults(),
                ShortCode.of("ABC1234567"));
    }
}
