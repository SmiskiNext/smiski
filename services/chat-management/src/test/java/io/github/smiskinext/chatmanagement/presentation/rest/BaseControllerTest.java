package io.github.smiskinext.chatmanagement.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.phunguy65.zms.shared.infrastructure.web.FailData;
import io.github.phunguy65.zms.shared.infrastructure.web.JsendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BaseControllerTest {

    private final TestController controller = new TestController();

    @Test
    void errorResponse_roomNotFound_returns404FailEnvelope() {
        ResponseEntity<JsendResponse<?>> response =
                controller.map(new ChatError.RoomNotFound("room-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo("fail");
        FailData body = (FailData) response.getBody().data();
        assertThat(body.code().toString()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void errorResponse_unauthorized_returns403FailEnvelope() {
        ResponseEntity<JsendResponse<?>> response =
                controller.map(new ChatError.Unauthorized("Meeting has ended"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().status()).isEqualTo("fail");
        FailData body = (FailData) response.getBody().data();
        assertThat(body.code().toString()).isEqualTo("UNAUTHORIZED");
        assertThat(body.message()).isEqualTo("Unauthorized: Meeting has ended");
    }

    @Test
    void errorResponse_messageTooLong_returns400FailEnvelope() {
        ResponseEntity<JsendResponse<?>> response =
                controller.map(new ChatError.MessageTooLong(100, 150));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo("fail");
        FailData body = (FailData) response.getBody().data();
        assertThat(body.code().toString()).isEqualTo("MESSAGE_TOO_LONG");
        assertThat(body.message()).contains("exceeds maximum length of 100 characters");
    }

    @Test
    void errorResponse_persistenceFailure_returns500ErrorEnvelope() {
        ResponseEntity<JsendResponse<?>> response =
                controller.map(new ChatError.PersistenceFailure("Mongo down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).contains("Persistence failure");
        assertThat(response.getBody().data()).isNull();
    }

    private static final class TestController extends BaseController {

        @SuppressWarnings("unchecked")
        private ResponseEntity<JsendResponse<?>> map(ChatError error) {
            return (ResponseEntity<JsendResponse<?>>) (ResponseEntity<?>) errorResponse(error);
        }
    }
}
