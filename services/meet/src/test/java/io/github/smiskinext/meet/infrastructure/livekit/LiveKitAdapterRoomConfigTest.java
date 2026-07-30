package io.github.smiskinext.meet.infrastructure.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.shared.domain.Result;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

class LiveKitAdapterRoomConfigTest {

    private static final String TENANT_ID = "tenant-42";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RoomServiceClient roomServiceClient = mock(RoomServiceClient.class);

    private final LiveKitAdapter adapter = new LiveKitAdapter(
            new LiveKitProperties(
                    "http://localhost:7880",
                    "ws://localhost:7880",
                    "test-key",
                    "test-secret-must-be-at-least-32-characters",
                    "livekit-webhook-events",
                    1800),
            roomServiceClient);

    @Test
    void hostTokenCarriesRoomConfigurationMetadataEqualToTenant() {
        assertRoomMetadataEqualsTenant(ParticipantRole.HOST);
    }

    @Test
    void participantTokenCarriesRoomConfigurationMetadataEqualToTenant() {
        assertRoomMetadataEqualsTenant(ParticipantRole.PARTICIPANT);
    }

    private void assertRoomMetadataEqualsTenant(ParticipantRole role) {
        MeetingId meetingId = MeetingId.of(UUID.randomUUID());
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meetingId);
        LiveKitTokenRequest request = new LiveKitTokenRequest(
                roomName,
                LiveKitIdentity.of("account-1:device-1"),
                "Alice",
                role,
                new ParticipantAttributes(null, role),
                TENANT_ID,
                new MeetingSettings(AdmissionPolicy.ALLOW_ALL, 50, true, true, true, true));

        Result<String, ?> result = adapter.generateToken(request);
        assertThat(result.isSuccess()).isTrue();
        String jwt = ((Result.Success<String, ?>) result).value();

        JsonNode roomConfig = decodePayload(jwt).path("roomConfig");
        assertThat(roomConfig.isMissingNode()).isFalse();
        assertThat(roomConfig.path("metadata").asText()).isEqualTo(TENANT_ID);
        assertThat(roomConfig.path("name").asText()).isEqualTo(roomName.value());
    }

    @Test
    void deleteRoomReturnsSuccessOnSuccessfulHttpResponse() throws IOException {
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(UUID.randomUUID()));
        @SuppressWarnings("unchecked")
        Call<Void> call = mock(Call.class);
        when(roomServiceClient.deleteRoom(eq(roomName.value()))).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(null));

        Result<Void, MeetingError> result = adapter.deleteRoom(roomName);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void deleteRoomReturnsFailureOnUnsuccessfulHttpResponse() throws IOException {
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(UUID.randomUUID()));
        @SuppressWarnings("unchecked")
        Call<Void> call = mock(Call.class);
        when(roomServiceClient.deleteRoom(eq(roomName.value()))).thenReturn(call);
        when(call.execute())
                .thenReturn(Response.error(
                        404, ResponseBody.create(MediaType.parse("application/json"), "{}")));

        Result<Void, MeetingError> result = adapter.deleteRoom(roomName);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    @Test
    void deleteRoomReturnsFailureWhenClientThrows() throws IOException {
        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(MeetingId.of(UUID.randomUUID()));
        @SuppressWarnings("unchecked")
        Call<Void> call = mock(Call.class);
        when(roomServiceClient.deleteRoom(eq(roomName.value()))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("network down"));

        Result<Void, MeetingError> result = adapter.deleteRoom(roomName);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    private JsonNode decodePayload(String jwt) {
        String[] segments = jwt.split("\\.");
        assertThat(segments).hasSize(3);
        String payload =
                new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JWT payload", e);
        }
    }
}
