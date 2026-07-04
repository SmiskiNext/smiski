package io.github.smiskinext.chatmanagement.infrastructure.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.phunguy65.zms.shared.domain.Result;
import java.lang.reflect.Method;
import kotlin.KotlinNullPointerException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for ChatLiveKitAdapter.
 *
 * <p>Tests the adapter's broadcast contract: given a {@link ChatMessage},
 * {@code broadcastMessage()} must return the correct {@link Result}.
 *
 * <p>{@link io.livekit.server.RoomServiceClient} is mocked via Mockito's inline mock maker
 * (byte-buddy agent). {@code sendData()} is mocked to return a controllable {@link Call}.
 */
@ExtendWith(MockitoExtension.class)
class ChatLiveKitAdapterTest {

    @Mock
    private io.livekit.server.RoomServiceClient roomServiceClient;

    @Mock
    private Call<Void> call;

    private ChatLiveKitAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new ChatLiveKitAdapter(roomServiceClient, objectMapper);
    }

    // ─── Success path ───────────────────────────────────────────────────────

    @Test
    void broadcastMessage_success_returnsSuccess() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(null));

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage("Hello!"));

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void broadcastMessage_http500_returnsPersistenceFailure() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        Response<Void> errorResp = Response.error(
                500, ResponseBody.create(MediaType.parse("text/plain"), "Server Error"));
        when(call.execute()).thenReturn(errorResp);

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage("Hello!"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Void, ChatError>) result).error())
                .isInstanceOf(ChatError.PersistenceFailure.class);
    }

    // ─── KNPE bug handling ──────────────────────────────────────────────────

    @Test
    void broadcastMessage_KotlinNullPointerException_treatedAsSuccess() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        when(call.execute()).thenThrow(new KotlinNullPointerException());

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage("Hello!"));

        // KNPE is the known SDK 0.12.1 bug — treated as success
        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void broadcastMessage_knpeAsCause_treatedAsSuccess() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        // KotlinNullPointerException wrapped as cause
        RuntimeException knpeCause = new KotlinNullPointerException();
        when(call.execute()).thenThrow(new RuntimeException("some error", knpeCause));

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage("Hello!"));

        // KNPE as cause → detected as KNPE bug → success
        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void broadcastMessage_otherException_returnsPersistenceFailure() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        when(call.execute()).thenThrow(new RuntimeException("Connection refused"));

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage("Hello!"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Void, ChatError>) result).error())
                .isInstanceOf(ChatError.PersistenceFailure.class);
    }

    // ─── Payload size ────────────────────────────────────────────────────────

    @Test
    void broadcastMessage_payloadExceedsLimit_returnsMessageTooLong() {
        String huge = "x".repeat(20_000);

        Result<Void, ChatError> result = adapter.broadcastMessage("room-1", makeMessage(huge));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Void, ChatError>) result).error())
                .isInstanceOf(ChatError.MessageTooLong.class);
    }

    // ─── Serialization ──────────────────────────────────────────────────────

    @Test
    void broadcastMessage_specialCharacters_serializedCorrectly() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(null));

        Result<Void, ChatError> result = adapter.broadcastMessage(
                "room-1",
                ChatMessage.send(1L, "room-1", "user-1", "Alice", "Hello \"world\" & <tag>", null));

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void broadcastMessage_unicode_serializedCorrectly() throws Exception {
        when(roomServiceClient.sendData(any(), any(), any(), anyList())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(null));

        Result<Void, ChatError> result = adapter.broadcastMessage(
                "room-1",
                ChatMessage.send(1L, "room-1", "user-1", "Alice", "Tiếng Việt 🎉 日本語 한국어", null));

        assertThat(result).isInstanceOf(Result.Success.class);
    }

    // ─── isKnownKnpeBug unit tests ─────────────────────────────────────────

    @Test
    void isKnownKnpeBug_detectsKotlinNullPointerException() throws Exception {
        Method m = ChatLiveKitAdapter.class.getDeclaredMethod("isKnownKnpeBug", Exception.class);
        m.setAccessible(true);
        assertThat(m.invoke(adapter, new KotlinNullPointerException())).isEqualTo(true);
    }

    @Test
    void isKnownKnpeBug_rejectsNullCauseWithoutMessageInIt() throws Exception {
        Method m = ChatLiveKitAdapter.class.getDeclaredMethod("isKnownKnpeBug", Exception.class);
        m.setAccessible(true);
        assertThat(m.invoke(adapter, new RuntimeException((Throwable) null))).isEqualTo(false);
    }

    @Test
    void isKnownKnpeBug_detectsNullInMessage() throws Exception {
        Method m = ChatLiveKitAdapter.class.getDeclaredMethod("isKnownKnpeBug", Exception.class);
        m.setAccessible(true);
        assertThat(m.invoke(adapter, new RuntimeException("something null here")))
                .isEqualTo(true);
    }

    @Test
    void isKnownKnpeBug_rejectsOtherExceptions() throws Exception {
        Method m = ChatLiveKitAdapter.class.getDeclaredMethod("isKnownKnpeBug", Exception.class);
        m.setAccessible(true);
        assertThat(m.invoke(adapter, new RuntimeException("Connection refused")))
                .isEqualTo(false);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private ChatMessage makeMessage(String content) {
        return ChatMessage.send(1L, "room-1", "user-1", "Alice", content, null);
    }
}
