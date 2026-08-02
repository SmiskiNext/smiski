package io.github.smiskinext.notification.application.command;

import io.github.smiskinext.shared.application.Command;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Command to subscribe to a meeting's host event stream or to a join request's decision stream.
 *
 * @param meetingId       the meeting to subscribe to (always required)
 * @param isRequestStream {@code true} to subscribe to a join-request decision stream
 * @param requestId       the join request id when {@code isRequestStream} is {@code true}
 */
public record SubscribeMeetingEventsCommand(
        UUID meetingId, boolean isRequestStream, @Nullable UUID requestId) implements Command {}
