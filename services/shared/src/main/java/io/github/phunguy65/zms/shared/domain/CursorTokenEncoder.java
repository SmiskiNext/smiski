package io.github.phunguy65.zms.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for encoding and decoding HMAC-signed cursor tokens used in keyset pagination.
 *
 * <p>This interface lives in the domain layer so that application-layer use cases can depend on it
 * without importing infrastructure types. The concrete implementation ({@code CursorEncoder}) lives
 * in {@code shared.infrastructure.web} and is injected at runtime by Spring.
 */
public interface CursorTokenEncoder {

    /**
     * Encodes a keyset cursor position into a signed, opaque token.
     *
     * @param createdAt the {@code created_at} timestamp of the last row on the current page
     * @param id        the {@code id} (UUID) of the last row on the current page
     * @return Base64url-encoded signed token
     */
    String encode(Instant createdAt, UUID id);

    /**
     * Decodes and verifies a cursor token.
     *
     * @param token the Base64url-encoded token from a previous response's {@code nextPageToken}
     * @return {@link Result.Success} containing a {@link ScrollCursor}, or
     *         {@link Result.Failure} with {@link CursorErrorCode#INVALID_CURSOR} if the token
     *         is malformed or the signature is invalid
     */
    Result<ScrollCursor, CursorErrorCode> decode(String token);
}
