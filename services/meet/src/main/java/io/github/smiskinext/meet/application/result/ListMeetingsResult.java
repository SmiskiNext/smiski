package io.github.smiskinext.meet.application.result;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of a tenant meeting listing.
 *
 * <p>Framework-agnostic: no Jackson or OpenAPI annotations. The presentation layer maps this into
 * the shared page envelope.
 *
 * @param items         the page of meeting summaries in order
 * @param hasNext       whether further pages exist
 * @param nextPageToken the opaque cursor for the next page, or {@code null} on the last page
 */
public record ListMeetingsResult(
        List<Item> items, boolean hasNext, @Nullable String nextPageToken) {

    public ListMeetingsResult {
        items = List.copyOf(items);
    }

    /**
     * A single listed meeting.
     *
     * @param id          the meeting id
     * @param hostId      the host account id
     * @param shortCode   the short code
     * @param title       the meeting title
     * @param description the meeting description
     * @param issueId     the linked Jira issue id
     * @param issueKey    the linked Jira issue key
     * @param projectKey  the linked Jira project key
     * @param type        the meeting type
     * @param status      the meeting status
     * @param startTime   the scheduled start time, or {@code null} for meetings without one
     * @param endTime     the end time, or {@code null}
     * @param createdAt   the creation time
     * @param settings    the meeting settings
     */
    public record Item(
            UUID id,
            String hostId,
            String shortCode,
            @Nullable String title,
            @Nullable String description,
            String issueId,
            String issueKey,
            String projectKey,
            MeetingType type,
            MeetingStatus status,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            Instant createdAt,
            Settings settings) {

        /**
         * Meeting room settings projection.
         *
         * @param admissionPolicy  the admission policy name
         * @param maxParticipants  the participant cap
         * @param allowScreenShare whether screen sharing is allowed
         * @param chatEnabled      whether chat is enabled
         * @param allowMicrophone  whether microphones are allowed
         * @param allowVideo       whether video is allowed
         */
        public record Settings(
                String admissionPolicy,
                int maxParticipants,
                boolean allowScreenShare,
                boolean chatEnabled,
                boolean allowMicrophone,
                boolean allowVideo) {}
    }
}
