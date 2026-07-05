package io.github.smiskinext.chatmanagement.presentation.rest;

import io.github.smiskinext.chatmanagement.domain.model.ChatError;
import io.github.smiskinext.chatmanagement.domain.model.ChatErrorCode;
import io.github.smiskinext.shared.infrastructure.web.FailData;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Shared controller base that centralises {@link ChatError} response mapping. */
abstract class BaseController {

    protected <T> ResponseEntity<JsendResponse<T>> errorResponse(ChatError error) {
        HttpStatus status =
                switch (error) {
                    case ChatError.RoomNotFound e -> HttpStatus.NOT_FOUND;
                    case ChatError.Unauthorized e -> HttpStatus.FORBIDDEN;
                    case ChatError.MessageTooLong e -> HttpStatus.BAD_REQUEST;
                    case ChatError.PersistenceFailure e -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>)
                    ResponseEntity.status(status).body(JsendResponse.error(error.message()));
        }

        ChatErrorCode code =
                switch (error) {
                    case ChatError.RoomNotFound e -> ChatErrorCode.ROOM_NOT_FOUND;
                    case ChatError.Unauthorized e -> ChatErrorCode.UNAUTHORIZED;
                    case ChatError.MessageTooLong e -> ChatErrorCode.MESSAGE_TOO_LONG;
                    case ChatError.PersistenceFailure e -> ChatErrorCode.PERSISTENCE_FAILURE;
                };

        return (ResponseEntity<JsendResponse<T>>) (ResponseEntity<?>) ResponseEntity.status(status)
                .body(JsendResponse.fail(new FailData(error.message(), code, List.of())));
    }

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<JsendResponse<T>> unauthenticated() {
        return (ResponseEntity<JsendResponse<T>>)
                (ResponseEntity<?>) ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(JsendResponse.error("Authentication required"));
    }
}
