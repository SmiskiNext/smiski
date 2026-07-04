package io.github.smiskinext.meetingmanagement.infrastructure.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantGrants;
import java.util.List;
import livekit.LivekitModels;
import org.junit.jupiter.api.Test;

class LiveKitAdapterTest {

    private final LiveKitAdapter adapter = new LiveKitAdapter(null, null, null);

    @Test
    void toPermission_setsCanPublishSourcesFromAllowedSources() {
        var grants = new ParticipantGrants(
                true,
                true,
                true,
                List.of("microphone", "camera", "screen_share", "screen_share_audio"));

        LivekitModels.ParticipantPermission permission = adapter.toPermission(grants);

        assertThat(permission.getCanPublishSourcesList())
                .containsExactly(
                        LivekitModels.TrackSource.MICROPHONE,
                        LivekitModels.TrackSource.CAMERA,
                        LivekitModels.TrackSource.SCREEN_SHARE,
                        LivekitModels.TrackSource.SCREEN_SHARE_AUDIO);
    }

    @Test
    void toPermission_omitsCanPublishSourcesWhenAllowedSourcesEmpty() {
        var grants = ParticipantGrants.speaker();

        LivekitModels.ParticipantPermission permission = adapter.toPermission(grants);

        assertThat(permission.getCanPublishSourcesList()).isEmpty();
    }
}
