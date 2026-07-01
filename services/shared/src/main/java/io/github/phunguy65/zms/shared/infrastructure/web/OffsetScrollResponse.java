package io.github.phunguy65.zms.shared.infrastructure.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Generic JSON response envelope for offset-based list endpoints. */
public record OffsetScrollResponse<T>(
        List<T> content, int size, @Nullable Integer nextOffset) {}
