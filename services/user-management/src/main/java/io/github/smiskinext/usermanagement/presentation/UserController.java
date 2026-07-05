package io.github.smiskinext.usermanagement.presentation;

import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.shared.infrastructure.web.CursorScrollResponse;
import io.github.smiskinext.shared.infrastructure.web.FailData;
import io.github.smiskinext.shared.infrastructure.web.JsendResponse;
import io.github.smiskinext.usermanagement.application.response.UserResponse;
import io.github.smiskinext.usermanagement.application.usecase.GetUserUseCase;
import io.github.smiskinext.usermanagement.application.usecase.SearchUsersUseCase;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.presentation.request.SearchUsersRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Users", description = "User lookup and search")
public class UserController extends BaseController {

    private final GetUserUseCase getUserUseCase;
    private final SearchUsersUseCase searchUsersUseCase;
    private final CursorTokenEncoder cursorTokenEncoder;

    public UserController(
            GetUserUseCase getUserUseCase,
            SearchUsersUseCase searchUsersUseCase,
            CursorTokenEncoder cursorTokenEncoder) {
        this.getUserUseCase = getUserUseCase;
        this.searchUsersUseCase = searchUsersUseCase;
        this.cursorTokenEncoder = cursorTokenEncoder;
    }

    @Operation(summary = "Get user by ID")
    @GetMapping(value = "/{version}/users/{id}", version = "1.0")
    public ResponseEntity<JsendResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        return switch (getUserUseCase.execute(UserId.of(id))) {
            case Result.Success<UserResponse, AuthError> s ->
                ResponseEntity.ok(JsendResponse.success(s.value()));
            case Result.Failure<UserResponse, AuthError> f -> errorResponse(f.error());
        };
    }

    @Operation(summary = "Search users with cursor pagination")
    @SuppressWarnings("unchecked")
    @GetMapping(value = "/{version}/users:search", version = "1.0")
    public ResponseEntity<JsendResponse<CursorScrollResponse<UserResponse>>> searchUsers(
            @Valid @ModelAttribute SearchUsersRequest request) {
        if (request.pageToken().isEmpty()) {
            return executeSearch(request, null);
        }
        var decodeResult = cursorTokenEncoder.decode(request.pageToken().get());
        return switch (decodeResult) {
            case Result.Failure<ScrollCursor, CursorErrorCode> f ->
                (ResponseEntity<JsendResponse<CursorScrollResponse<UserResponse>>>)
                        (ResponseEntity<?>) ResponseEntity.badRequest()
                                .body(JsendResponse.fail(
                                        new FailData(f.error().name(), f.error(), List.of())));
            case Result.Success<ScrollCursor, CursorErrorCode> s ->
                executeSearch(request, s.value());
        };
    }

    private ResponseEntity<JsendResponse<CursorScrollResponse<UserResponse>>> executeSearch(
            SearchUsersRequest request, ScrollCursor cursor) {
        var pageResult = searchUsersUseCase.execute(request.toQuery(), cursor);

        String nextPageToken = null;
        if (pageResult.hasNext() && !pageResult.items().isEmpty()) {
            var last = pageResult.items().getLast();
            nextPageToken = cursorTokenEncoder.encode(last.createdAt(), last.id());
        }

        var response =
                new CursorScrollResponse<>(pageResult.items(), request.pageSize(), nextPageToken);
        return ResponseEntity.ok(JsendResponse.success(response));
    }
}
