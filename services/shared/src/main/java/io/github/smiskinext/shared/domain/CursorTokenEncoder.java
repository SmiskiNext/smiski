package io.github.smiskinext.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for encoding and decoding HMAC-signed cursor tokens used in keyset pagination.
 *
 * <p>This interface lives in the domain layer so that application-layer use cases can depend on it
 * without importing infrastructure types. The concrete implementation ({@code CursorEncoder}) lives
 * in {@code shared.infrastructure.web} and is injected at runtime by Spring.
 *
 * <p>Every operation accepts a caller-supplied {@code context} that is bound into the signature but
 * never stored in the token. A token only verifies when decoded under the exact context it was
 * issued for, which lets callers bind a token to out-of-band state such as a sort mode without
 * having to sign that state themselves.
 */
public interface CursorTokenEncoder {

    /**
     * Encodes a keyset cursor position into a signed, opaque token bound to {@code context}.
     *
     * @param context   caller-supplied binding value mixed into the signature; must be reproduced
     *                  verbatim at decode time
     * @param createdAt the {@code created_at} timestamp of the last row on the current page
     * @param id        the {@code id} (UUID) of the last row on the current page
     * @return Base64url-encoded signed token
     */
    String encode(String context, Instant createdAt, UUID id);

    /**
     * Decodes and verifies a cursor token issued for {@code context}.
     *
     * @param context the same binding value used at encode time
     * @param token   the Base64url-encoded token from a previous response's {@code nextPageToken}
     * @return {@link Result.Success} containing a {@link ScrollCursor}, or
     *         {@link Result.Failure} with {@link CursorErrorCode#INVALID_CURSOR} if the token
     *         is malformed, not canonically encoded, was issued for a different context, or its
     *         signature is invalid
     */
    Result<ScrollCursor, CursorErrorCode> decode(String context, String token);
}
