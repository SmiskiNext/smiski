package io.github.smiskinext.shared.domain;

import java.util.Locale;

/**
 * Contract for machine-readable error codes used to build {@code application/problem+json}
 * responses.
 *
 * <p>Each service defines its own enum implementing this interface (e.g. {@code MeetingErrorCode}).
 * Constants are module-namespaced to prevent collisions. {@code CommonErrorCode} (in the shared
 * infrastructure layer) holds cross-cutting codes such as {@code VALIDATION_ERROR}.
 *
 * <p>An error code carries two stable, locale-independent facets:
 *
 * <ul>
 *   <li>{@link #code()} — the machine-readable identifier surfaced as the {@code code} extension
 *       member of the Problem Details body (clients may switch logic on it).
 *   <li>{@link #category()} — the semantic {@link ErrorCategory} the infrastructure layer maps to a
 *       concrete HTTP status.
 * </ul>
 *
 * <p>Human-readable {@code title}/{@code detail} text is never stored here; it is resolved at
 * response-building time from a message bundle keyed by {@link #titleKey()} / {@link #detailKey()},
 * localized to the request {@code Accept-Language}.
 */
public interface ErrorCode {

    /**
     * Stable machine-readable identifier (e.g. {@code MEETING_NOT_FOUND}).
     *
     * <p>Defaults to the enum constant name for enum implementations.
     */
    String code();

    /** Semantic category the infrastructure layer maps to an HTTP status. */
    ErrorCategory category();

    /**
     * Message-bundle key for the localized short {@code title}.
     *
     * <p>Derived from {@link #code()} as {@code error.<kebab-case-code>.title}, e.g.
     * {@code MEETING_NOT_FOUND} → {@code error.meeting-not-found.title}.
     */
    default String titleKey() {
        return "error." + kebab() + ".title";
    }

    /**
     * Message-bundle key for the localized long {@code detail}.
     *
     * <p>Derived from {@link #code()} as {@code error.<kebab-case-code>.detail}. The bundle entry
     * may contain positional placeholders ({@code {0}}, {@code {1}} …) filled from the error's
     * message arguments.
     */
    default String detailKey() {
        return "error." + kebab() + ".detail";
    }

    /** Lower-cased, hyphen-separated form of {@link #code()} used to derive bundle keys. */
    private String kebab() {
        return code().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
