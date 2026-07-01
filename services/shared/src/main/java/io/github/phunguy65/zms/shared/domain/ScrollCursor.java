package io.github.phunguy65.zms.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoded keyset cursor position for use in repository queries.
 *
 * <p>Pure domain value object — no framework dependencies. Produced by
 * {@code CursorEncoder.decode()} in the infrastructure layer and consumed by
 *
 * @param createdAt the {@code created_at} timestamp of the last row on the previous page
 * @param id        the {@code id} of the last row on the previous page
 */
public record ScrollCursor(Instant createdAt, UUID id) {}
