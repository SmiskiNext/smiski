package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ParticipantGrants#fromSettings(MeetingSettings, ParticipantRole)}.
 *
 * <p>Verifies the permission derivation logic:
 * <ul>
 *   <li>HOST — full permissions regardless of settings</li>
 *   <li>GUEST — subscribe-only regardless of settings</li>
 *   <li>PARTICIPANT — derived from meeting settings</li>
 * </ul>
 */
class ParticipantGrantsTest {

    @Test
    void fromSettings_host_alwaysFullPermissions() {
        MeetingSettings restrictiveSettings = settings(false, false, false, false);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(restrictiveSettings, ParticipantRole.HOST);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_host_withNullSettings_alwaysFullPermissions() {
        ParticipantGrants grants = ParticipantGrants.fromSettings(null, ParticipantRole.HOST);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_guest_alwaysSubscribeOnly() {
        MeetingSettings permissiveSettings = settings(true, true, true, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(permissiveSettings, ParticipantRole.GUEST);

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isFalse();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_guest_withNullSettings_alwaysSubscribeOnly() {
        ParticipantGrants grants = ParticipantGrants.fromSettings(null, ParticipantRole.GUEST);

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isFalse();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_allMediaEnabled_fullPublish() {
        MeetingSettings settings = settings(true, true, true, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_microphoneOnlyEnabled_canPublish() {
        MeetingSettings settings = settings(true, false, false, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_videoOnlyEnabled_canPublish() {
        MeetingSettings settings = settings(false, true, false, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_screenShareOnlyEnabled_canPublish() {
        MeetingSettings settings = settings(false, false, true, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_allMediaDisabled_cannotPublish() {
        MeetingSettings settings = settings(false, false, false, true);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isTrue(); // chat still enabled
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_chatDisabled_cannotPublishData() {
        MeetingSettings settings = settings(true, true, true, false);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isFalse();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_allDisabled_minimalPermissions() {
        MeetingSettings settings = settings(false, false, false, false);

        ParticipantGrants grants =
                ParticipantGrants.fromSettings(settings, ParticipantRole.PARTICIPANT);

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isFalse();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void fromSettings_participant_nullSettings_fallsBackToFullPermissions() {
        ParticipantGrants grants =
                ParticipantGrants.fromSettings(null, ParticipantRole.PARTICIPANT);

        // Backwards compatibility: null settings means full permissions
        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void factoryMethods_speaker_fullPermissions() {
        ParticipantGrants grants = ParticipantGrants.speaker();

        assertThat(grants.canPublish()).isTrue();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void factoryMethods_viewer_canChatButNotPublishMedia() {
        ParticipantGrants grants = ParticipantGrants.viewer();

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isTrue();
        assertThat(grants.canSubscribe()).isTrue();
    }

    @Test
    void factoryMethods_observer_subscribeOnly() {
        ParticipantGrants grants = ParticipantGrants.observer();

        assertThat(grants.canPublish()).isFalse();
        assertThat(grants.canPublishData()).isFalse();
        assertThat(grants.canSubscribe()).isTrue();
    }

    private static MeetingSettings settings(
            boolean allowMicrophone,
            boolean allowVideo,
            boolean allowScreenShare,
            boolean chatEnabled) {
        return new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL,
                true, // allowGuest
                100, // maxParticipants
                allowScreenShare,
                chatEnabled,
                allowMicrophone,
                allowVideo,
                null); // password
    }
}
