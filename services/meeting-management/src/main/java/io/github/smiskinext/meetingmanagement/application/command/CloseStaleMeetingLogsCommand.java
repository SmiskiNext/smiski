package io.github.smiskinext.meetingmanagement.application.command;

import java.util.UUID;

/**
 * Command to bulk-close all active participation logs for a meeting.
 *
 * <p>Triggered by the {@code room_finished} LiveKit webhook, or as a belt-and-suspenders
 * call from {@code EndMeetingUseCase} after the LiveKit room is deleted.
 */
public record CloseStaleMeetingLogsCommand(UUID meetingId) {}
