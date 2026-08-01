package io.github.smiskinext.meet.infrastructure.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.shared.domain.Result;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import livekit.LivekitModels;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;

class LiveKitAdapterUpdatePermissionsTest {

    private static final LiveKitRoomName ROOM_NAME = new LiveKitRoomName("meeting-1");
    private static final String IDENTITY = "participant-1:device-1";

    private final RoomServiceClient roomServiceClient = mock(RoomServiceClient.class);
    private final LiveKitAdapter adapter = new LiveKitAdapter(properties(), roomServiceClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatePermissionsPreservesCurrentCanPublishData() throws IOException {
        LivekitModels.ParticipantPermission existingPermission =
                LivekitModels.ParticipantPermission.newBuilder()
                        .setCanPublishData(true)
                        .build();
        LivekitModels.ParticipantInfo participantInfo = LivekitModels.ParticipantInfo.newBuilder()
                .setIdentity(IDENTITY)
                .setPermission(existingPermission)
                .build();
        stubListParticipants(List.of(participantInfo));
        stubUpdateParticipant(Response.success(participantInfo));

        ParticipantGrants grants =
                new ParticipantGrants(true, false, true, List.of("microphone", "camera"));

        Result<Void, MeetingError> result =
                adapter.updateParticipantPermissions(ROOM_NAME, IDENTITY, grants);

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<LivekitModels.ParticipantPermission> permCaptor =
                ArgumentCaptor.forClass(LivekitModels.ParticipantPermission.class);
        verify(roomServiceClient)
                .updateParticipant(
                        eq(ROOM_NAME.value()),
                        eq(IDENTITY),
                        isNull(),
                        isNull(),
                        permCaptor.capture(),
                        isNull());
        LivekitModels.ParticipantPermission sent = permCaptor.getValue();
        assertThat(sent.getCanPublishData()).isTrue();
        assertThat(sent.getCanPublish()).isTrue();
        assertThat(sent.getCanPublishSourcesList())
                .containsExactlyInAnyOrder(
                        LivekitModels.TrackSource.MICROPHONE, LivekitModels.TrackSource.CAMERA);
    }

    @Test
    void updatePermissionsExcludesScreenShareWhenDisabled() throws IOException {
        LivekitModels.ParticipantPermission existingPermission =
                LivekitModels.ParticipantPermission.newBuilder()
                        .setCanPublishData(false)
                        .build();
        LivekitModels.ParticipantInfo participantInfo = LivekitModels.ParticipantInfo.newBuilder()
                .setIdentity(IDENTITY)
                .setPermission(existingPermission)
                .build();
        stubListParticipants(List.of(participantInfo));
        stubUpdateParticipant(Response.success(participantInfo));

        ParticipantGrants grants =
                new ParticipantGrants(true, false, true, List.of("microphone", "camera"));

        Result<Void, MeetingError> result =
                adapter.updateParticipantPermissions(ROOM_NAME, IDENTITY, grants);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<LivekitModels.ParticipantPermission> permCaptor =
                ArgumentCaptor.forClass(LivekitModels.ParticipantPermission.class);
        verify(roomServiceClient)
                .updateParticipant(
                        eq(ROOM_NAME.value()),
                        eq(IDENTITY),
                        isNull(),
                        isNull(),
                        permCaptor.capture(),
                        isNull());
        LivekitModels.ParticipantPermission sent = permCaptor.getValue();
        assertThat(sent.getCanPublishData()).isFalse();
        assertThat(sent.getCanPublishSourcesList())
                .doesNotContain(
                        LivekitModels.TrackSource.SCREEN_SHARE,
                        LivekitModels.TrackSource.SCREEN_SHARE_AUDIO);
    }

    @Test
    void updatePermissionsReturnsLiveKitUnavailableOnIOException() throws IOException {
        LivekitModels.ParticipantInfo participantInfo = LivekitModels.ParticipantInfo.newBuilder()
                .setIdentity(IDENTITY)
                .setPermission(LivekitModels.ParticipantPermission.newBuilder()
                        .setCanPublishData(true)
                        .build())
                .build();
        stubListParticipants(List.of(participantInfo));

        @SuppressWarnings("unchecked")
        Call<LivekitModels.ParticipantInfo> updateCall = mock(Call.class);
        when(updateCall.execute()).thenThrow(new IOException("connection reset"));
        when(roomServiceClient.updateParticipant(
                        eq(ROOM_NAME.value()),
                        eq(IDENTITY),
                        isNull(),
                        isNull(),
                        any(LivekitModels.ParticipantPermission.class),
                        isNull()))
                .thenReturn(updateCall);

        ParticipantGrants grants = new ParticipantGrants(true, false, true, List.of("microphone"));

        Result<Void, MeetingError> result =
                adapter.updateParticipantPermissions(ROOM_NAME, IDENTITY, grants);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    @Test
    void participantTokenExcludesScreenShareWhenDisabled() {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, false, true, true, true);
        LiveKitTokenRequest request = new LiveKitTokenRequest(
                ROOM_NAME,
                LiveKitIdentity.of("acc:dev1"),
                "Alice",
                ParticipantRole.PARTICIPANT,
                new ParticipantAttributes(null, ParticipantRole.PARTICIPANT),
                "tenant",
                settings);

        Result<String, ?> result = adapter.generateToken(request);
        assertThat(result.isSuccess()).isTrue();
        String jwt = ((Result.Success<String, ?>) result).value();
        JsonNode video = decodePayload(jwt).path("video");
        assertThat(video.path("canPublish").asBoolean()).isTrue();
        List<String> sources = new java.util.ArrayList<>();
        video.path("canPublishSources").forEach(n -> sources.add(n.asText()));
        assertThat(sources)
                .contains("microphone", "camera")
                .doesNotContain("screen_share", "screen_share_audio");
    }

    @Test
    void participantTokenGrantsNoPublishWhenAllMediaDisabled() {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, false, true, false, false);
        LiveKitTokenRequest request = new LiveKitTokenRequest(
                ROOM_NAME,
                LiveKitIdentity.of("acc:dev1"),
                "Alice",
                ParticipantRole.PARTICIPANT,
                new ParticipantAttributes(null, ParticipantRole.PARTICIPANT),
                "tenant",
                settings);

        Result<String, ?> result = adapter.generateToken(request);
        assertThat(result.isSuccess()).isTrue();
        String jwt = ((Result.Success<String, ?>) result).value();
        JsonNode video = decodePayload(jwt).path("video");
        assertThat(video.path("canPublish").asBoolean()).isFalse();
        assertThat(video.has("canPublishSources")).isFalse();
    }

    @Test
    void hostTokenStaysFullyPermissioned() {
        MeetingSettings settings =
                new MeetingSettings(AdmissionPolicy.MANUAL_APPROVAL, 50, false, true, false, false);
        LiveKitTokenRequest request = new LiveKitTokenRequest(
                ROOM_NAME,
                LiveKitIdentity.of("host:dev1"),
                "Host",
                ParticipantRole.HOST,
                new ParticipantAttributes(null, ParticipantRole.HOST),
                "tenant",
                settings);

        Result<String, ?> result = adapter.generateToken(request);
        assertThat(result.isSuccess()).isTrue();
        String jwt = ((Result.Success<String, ?>) result).value();
        JsonNode video = decodePayload(jwt).path("video");
        assertThat(video.path("canPublish").asBoolean()).isTrue();
        assertThat(video.has("canPublishSources")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private void stubListParticipants(List<LivekitModels.ParticipantInfo> participants)
            throws IOException {
        Call<List<LivekitModels.ParticipantInfo>> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(participants));
        when(roomServiceClient.listParticipants(ROOM_NAME.value())).thenReturn(call);
    }

    @SuppressWarnings("unchecked")
    private void stubUpdateParticipant(Response<LivekitModels.ParticipantInfo> response)
            throws IOException {
        Call<LivekitModels.ParticipantInfo> call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(roomServiceClient.updateParticipant(
                        eq(ROOM_NAME.value()),
                        eq(IDENTITY),
                        isNull(),
                        isNull(),
                        any(LivekitModels.ParticipantPermission.class),
                        isNull()))
                .thenReturn(call);
    }

    private JsonNode decodePayload(String jwt) {
        String[] segments = jwt.split("\\.");
        String payload =
                new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JWT payload", e);
        }
    }

    private static LiveKitProperties properties() {
        return new LiveKitProperties(
                "http://localhost:7880",
                "ws://localhost:7880",
                "test-key",
                "test-secret-must-be-at-least-32-characters",
                "livekit-webhook-events",
                1800);
    }
}
