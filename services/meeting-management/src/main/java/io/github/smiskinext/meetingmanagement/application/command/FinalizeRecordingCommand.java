package io.github.smiskinext.meetingmanagement.application.command;

import org.jspecify.annotations.Nullable;

/**
 * Command for finalizing a recording after LiveKit reports egress completion or failure.
 */
public record FinalizeRecordingCommand(
        String livekitEgressId,
        boolean successful,
        @Nullable String fileUrl,
        @Nullable String storagePath,
        @Nullable String errorMessage,
        int durationSeconds,
        long fileSizeBytes) {}
