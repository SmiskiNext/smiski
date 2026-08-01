package io.github.smiskinext.meet.infrastructure.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.shared.domain.Result;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

class LiveKitAdapterDeleteRoomTest {

    private static final LiveKitRoomName ROOM_NAME = new LiveKitRoomName("meeting-1");

    private final RoomServiceClient roomServiceClient = mock(RoomServiceClient.class);
    private final LiveKitAdapter adapter = new LiveKitAdapter(properties(), roomServiceClient);

    @Test
    void deleteRoomReturnsSuccessWhenServerAcknowledges() throws IOException {
        stubDeleteRoomResponse(Response.success(null));

        Result<Void, MeetingError> result = adapter.deleteRoom(ROOM_NAME);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void deleteRoomReturnsLiveKitUnavailableWhenServerRejectsRequest() throws IOException {
        Response<Void> errorResponse =
                Response.error(500, ResponseBody.create("boom", MediaType.parse("text/plain")));
        stubDeleteRoomResponse(errorResponse);

        Result<Void, MeetingError> result = adapter.deleteRoom(ROOM_NAME);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    @Test
    void deleteRoomReturnsLiveKitUnavailableWhenServerUnreachable() throws IOException {
        @SuppressWarnings("unchecked")
        Call<Void> call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("connection refused"));
        when(roomServiceClient.deleteRoom(ROOM_NAME.value())).thenReturn(call);

        Result<Void, MeetingError> result = adapter.deleteRoom(ROOM_NAME);

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<Void, MeetingError>) result).error())
                .isInstanceOf(MeetingError.LiveKitUnavailable.class);
    }

    private void stubDeleteRoomResponse(Response<Void> response) throws IOException {
        @SuppressWarnings("unchecked")
        Call<Void> call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(roomServiceClient.deleteRoom(ROOM_NAME.value())).thenReturn(call);
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
