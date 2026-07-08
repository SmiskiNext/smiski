package io.github.smiskinext.record.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.record.domain.RecordError;
import io.github.smiskinext.record.domain.event.RecordingCompletedEvent;
import io.github.smiskinext.record.domain.event.RecordingFailedEvent;
import io.github.smiskinext.record.domain.event.RecordingStartedEvent;
import io.github.smiskinext.record.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.record.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.record.domain.model.valueobject.MeetingId;
import io.github.smiskinext.record.domain.model.valueobject.RecordingId;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Recording aggregate root — separate from Meeting to allow independent lifecycle management.
 *
 * <p>Lifecycle driven by LiveKit egress webhooks:
 * <ol>
 *   <li>{@link #startFor} — created by {@code StartRecordingUseCase} (status: {@code PENDING}).
 *   <li>{@link #activate} — called by {@code egress_started} webhook handler
 *       (PENDING → RECORDING), sets {@code livekitEgressId}.
 *   <li>{@link #complete} — called by {@code egress_ended} webhook handler
 *       (RECORDING → COMPLETED), sets file URL and metrics from LiveKit {@code fileResults}.
 *   <li>{@link #fail} — called on {@code egress_ended} with error, or on timeout
 *       (PENDING/RECORDING → FAILED).
 * </ol>
 */
public class Recording extends AggregateRoot<RecordingId> {

    private final TenantId tenantId;
    private final RecordingId id;
    private final MeetingId meetingId;
    private final LiveKitRoomName livekitRoomName;
    private final Instant startedAt;
    private final Instant createdAt;

    private @Nullable LiveKitEgressId livekitEgressId;
    private @Nullable String fileUrl;
    private @Nullable String thumbnailUrl;
    private @Nullable String storagePath;
    private @Nullable String errorMessage;
    private RecordingStatus status;
    private @Nullable Instant endedAt;
    private int durationSeconds;
    private long fileSizeBytes;

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private Recording(
            TenantId tenantId,
            RecordingId id,
            MeetingId meetingId,
            LiveKitRoomName livekitRoomName,
            @Nullable LiveKitEgressId livekitEgressId,
            @Nullable String fileUrl,
            @Nullable String thumbnailUrl,
            @Nullable String storagePath,
            @Nullable String errorMessage,
            RecordingStatus status,
            Instant startedAt,
            @Nullable Instant endedAt,
            int durationSeconds,
            long fileSizeBytes,
            Instant createdAt) {
        this.tenantId = tenantId;
        this.id = id;
        this.meetingId = meetingId;
        this.livekitRoomName = livekitRoomName;
        this.livekitEgressId = livekitEgressId;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.storagePath = storagePath;
        this.errorMessage = errorMessage;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a new PENDING recording. Called by {@code StartRecordingUseCase} before
     * the LiveKit egress is confirmed. Registers {@code RecordingStartedEvent}.
     */
    public static Recording startFor(
            TenantId tenantId, MeetingId meetingId, LiveKitRoomName livekitRoomName) {
        RecordingId id = RecordingId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();
        Recording recording = new Recording(
                tenantId,
                id,
                meetingId,
                livekitRoomName,
                null,
                null,
                null,
                null,
                null,
                RecordingStatus.PENDING,
                now,
                null,
                0,
                0L,
                now);
        recording.registerEvent(new RecordingStartedEvent(
                UUID.randomUUID(), tenantId.value(), id.value(), meetingId.value(), now));
        return recording;
    }

    /**
     * Reconstitutes a Recording from persistence. No domain events registered.
     */
    public static Recording reconstitute(
            TenantId tenantId,
            RecordingId id,
            MeetingId meetingId,
            LiveKitRoomName livekitRoomName,
            @Nullable LiveKitEgressId livekitEgressId,
            @Nullable String fileUrl,
            @Nullable String thumbnailUrl,
            @Nullable String storagePath,
            @Nullable String errorMessage,
            RecordingStatus status,
            Instant startedAt,
            @Nullable Instant endedAt,
            int durationSeconds,
            long fileSizeBytes,
            Instant createdAt) {
        return new Recording(
                tenantId,
                id,
                meetingId,
                livekitRoomName,
                livekitEgressId,
                fileUrl,
                thumbnailUrl,
                storagePath,
                errorMessage,
                status,
                startedAt,
                endedAt,
                durationSeconds,
                fileSizeBytes,
                createdAt);
    }

    // -------------------------------------------------------------------------
    // Domain behaviours
    // -------------------------------------------------------------------------

    /**
     * Persists the LiveKit egress ID assigned by the initial start request while the recording is
     * still awaiting webhook confirmation.
     */
    public void assignEgressId(LiveKitEgressId egressId) {
        if (this.livekitEgressId != null && !this.livekitEgressId.equals(egressId)) {
            throw new IllegalStateException("Recording already linked to a different egress id");
        }
        this.livekitEgressId = egressId;
    }

    /**
     * Transitions PENDING → RECORDING. Called by the {@code egress_started} webhook handler.
     */
    public Result<Void, RecordError> activate(LiveKitEgressId egressId) {
        if (!status.canTransitionTo(RecordingStatus.RECORDING)) {
            return Result.failure(
                    new RecordError.InvalidRecordingTransition(status, RecordingStatus.RECORDING));
        }
        this.status = RecordingStatus.RECORDING;
        this.livekitEgressId = egressId;
        return Result.success();
    }

    /**
     * Transitions RECORDING → COMPLETED. Called by the {@code egress_ended} webhook handler.
     *
     * <p>{@code durationSeconds} should be derived from LiveKit's {@code fileResults[0].duration}
     * (nanoseconds) divided by {@code 1_000_000_000}.
     */
    public Result<Void, RecordError> complete(
            String fileUrl,
            String storagePath,
            @Nullable String thumbnailUrl,
            int durationSeconds,
            long fileSizeBytes) {
        if (!status.canTransitionTo(RecordingStatus.COMPLETED)) {
            return Result.failure(
                    new RecordError.InvalidRecordingTransition(status, RecordingStatus.COMPLETED));
        }
        this.status = RecordingStatus.COMPLETED;
        this.fileUrl = fileUrl;
        this.storagePath = storagePath;
        this.thumbnailUrl = thumbnailUrl;
        this.errorMessage = null;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.endedAt = Instant.now();
        registerEvent(new RecordingCompletedEvent(
                UUID.randomUUID(),
                tenantId.value(),
                id.value(),
                meetingId.value(),
                fileUrl,
                durationSeconds,
                fileSizeBytes,
                Instant.now()));
        return Result.success();
    }

    /**
     * Transitions PENDING or RECORDING → FAILED.
     */
    public Result<Void, RecordError> fail(@Nullable String errorMessage) {
        if (!status.canTransitionTo(RecordingStatus.FAILED)) {
            return Result.failure(
                    new RecordError.InvalidRecordingTransition(status, RecordingStatus.FAILED));
        }
        this.status = RecordingStatus.FAILED;
        this.fileUrl = null;
        this.storagePath = null;
        this.thumbnailUrl = null;
        this.durationSeconds = 0;
        this.fileSizeBytes = 0L;
        this.errorMessage = errorMessage;
        if (endedAt == null) endedAt = Instant.now();
        registerEvent(new RecordingFailedEvent(
                UUID.randomUUID(), tenantId.value(), id.value(), meetingId.value(), Instant.now()));
        return Result.success();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Override
    public RecordingId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MeetingId getMeetingId() {
        return meetingId;
    }

    public LiveKitRoomName getLivekitRoomName() {
        return livekitRoomName;
    }

    public Optional<LiveKitEgressId> getLivekitEgressId() {
        return Optional.ofNullable(livekitEgressId);
    }

    public Optional<String> getFileUrl() {
        return Optional.ofNullable(fileUrl);
    }

    public Optional<String> getThumbnailUrl() {
        return Optional.ofNullable(thumbnailUrl);
    }

    public Optional<String> getStoragePath() {
        return Optional.ofNullable(storagePath);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public RecordingStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Optional<Instant> getEndedAt() {
        return Optional.ofNullable(endedAt);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
