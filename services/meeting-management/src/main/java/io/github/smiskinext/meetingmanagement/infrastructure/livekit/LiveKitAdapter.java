package io.github.smiskinext.meetingmanagement.infrastructure.livekit;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.ParticipantRole;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantAttributes;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanUpdateOwnMetadata;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.VideoGrant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import livekit.LivekitEgress;
import livekit.LivekitModels;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit2.Response;

/**
 * LiveKit adapter — generates JWT access tokens and manages rooms via the LiveKit Server SDK.
 *
 * <p>Token identity format:
 * <ul>
 *   <li>Authenticated user: {@code "<userId>:<deviceId>"} — supports multi-device without
 *       triggering {@code DUPLICATE_IDENTITY} disconnects.
 *   <li>Guest: {@code "guest:<deviceId>"}
 * </ul>
 *
 * <p>Permission matrix by role:
 * <ul>
 *   <li>HOST — roomAdmin, canPublish, canPublishData, canSubscribe, canUpdateOwnMetadata
 *   <li>PARTICIPANT — canPublish, canPublishData, canSubscribe, canUpdateOwnMetadata
 *   <li>GUEST — canSubscribe, canUpdateOwnMetadata (subscribe + raise-hand metadata only)
 * </ul>
 *
 * <p>Room operations use blocking {@code call.execute()}, which is safe on virtual threads
 * (enabled via {@code spring.threads.virtual.enabled=true}). All infrastructure exceptions are
 * caught internally and returned as {@link MeetingError.LiveKitUnavailable}.
 */
@Component
public class LiveKitAdapter implements LiveKitPort {

    private static final Logger log = LoggerFactory.getLogger(LiveKitAdapter.class);

    private final LiveKitProperties props;
    private final RoomServiceClient roomServiceClient;
    private final EgressServiceClient egressServiceClient;

    public LiveKitAdapter(
            LiveKitProperties props,
            RoomServiceClient roomServiceClient,
            EgressServiceClient egressServiceClient) {
        this.props = props;
        this.roomServiceClient = roomServiceClient;
        this.egressServiceClient = egressServiceClient;
    }

    @Override
    public Result<String, MeetingError> generateToken(LiveKitTokenRequest request) {
        try {
            AccessToken token = new AccessToken(props.getApiKey(), props.getApiSecret());
            token.setIdentity(request.identity().value());
            token.setName(request.displayName());
            token.setTtl(props.getTokenExpirySeconds() * 1_000L);
            token.getAttributes().putAll(request.participantAttributes().toMap());
            token.addGrants(new RoomJoin(true), new RoomName(request.roomName().value()));
            token.addGrants(buildRoleGrants(request.role(), request.meetingSettings())
                    .toArray(new VideoGrant[0]));

            return Result.success(token.toJwt());
        } catch (Exception e) {
            log.warn(
                    "Failed to generate LiveKit token for room '{}': {}",
                    request.roomName().value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<LiveKitEgressId, MeetingError> startRoomCompositeEgress(
            MeetingId meetingId, LiveKitRoomName roomName) {
        LivekitEgress.EncodedFileOutput fileOutput = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFilepath(recordingFilepath(meetingId))
                .setFileType(LivekitEgress.EncodedFileType.MP4)
                .setS3(LivekitEgress.S3Upload.newBuilder()
                        .setAccessKey(props.getRecording().getAccessKey())
                        .setSecret(props.getRecording().getSecretKey())
                        .setBucket(props.getRecording().getBucket())
                        .setRegion(props.getRecording().getRegion())
                        .setEndpoint(props.getRecording().getEgressEndpoint())
                        .setForcePathStyle(props.getRecording().isForcePathStyle())
                        .build())
                .build();

        try {
            Response<LivekitEgress.EgressInfo> response = egressServiceClient
                    .startRoomCompositeEgress(
                            roomName.value(), fileOutput, props.getRecording().getLayout())
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn(
                        "Failed to start room composite egress for room '{}': {}",
                        roomName.value(),
                        msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            String egressId = response.body().getEgressId();
            log.info(
                    "Started room composite egress '{}' for room '{}'", egressId, roomName.value());
            return Result.success(LiveKitEgressId.of(egressId));
        } catch (IOException e) {
            log.warn(
                    "Network error starting room composite egress for room '{}': {}",
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> stopEgress(LiveKitEgressId egressId) {
        try {
            Response<LivekitEgress.EgressInfo> response =
                    egressServiceClient.stopEgress(egressId.value()).execute();

            if (!response.isSuccessful()) {
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn("Failed to stop egress '{}': {}", egressId.value(), msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info("Stopped LiveKit egress: {}", egressId.value());
            return Result.success();
        } catch (IOException e) {
            log.warn("Network error stopping egress '{}': {}", egressId.value(), e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    /**
     * Builds LiveKit video grants based on participant role and meeting settings.
     *
     * <p>Permission matrix:
     * <ul>
     *   <li>HOST — full admin permissions regardless of meeting settings</li>
     *   <li>GUEST — subscribe-only regardless of meeting settings</li>
     *   <li>PARTICIPANT — derived from meeting settings:
     *     <ul>
     *       <li>canPublish = true only if at least one media source is enabled</li>
     *       <li>canPublishData = chatEnabled</li>
     *       <li>canPublishSources = filtered list based on allowMicrophone, allowVideo, allowScreenShare</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    private List<VideoGrant> buildRoleGrants(
            ParticipantRole role, @Nullable MeetingSettings settings) {
        return switch (role) {
            case HOST ->
                List.of(
                        new RoomAdmin(true),
                        new CanPublish(true),
                        new CanPublishData(true),
                        new CanSubscribe(true),
                        new CanUpdateOwnMetadata(true));
            case PARTICIPANT -> {
                ParticipantGrants grants = ParticipantGrants.fromSettings(settings, role);
                List<VideoGrant> grantList = new ArrayList<>();
                grantList.add(new CanSubscribe(true));
                grantList.add(new CanUpdateOwnMetadata(true));
                grantList.add(new CanPublishData(grants.canPublishData()));

                if (settings != null && grants.canPublish()) {
                    // When at least one source is enabled, use source-level filtering
                    List<String> allowedSources = buildAllowedSources(settings);
                    if (!allowedSources.isEmpty()) {
                        grantList.add(new CanPublish(true));
                        grantList.add(new CanPublishSources(allowedSources));
                    } else {
                        // All sources disabled → canPublish=false
                        grantList.add(new CanPublish(false));
                    }
                } else if (settings == null) {
                    // Backwards compatibility: no settings → full publish
                    grantList.add(new CanPublish(true));
                } else {
                    // All sources disabled → canPublish=false
                    grantList.add(new CanPublish(false));
                }

                yield List.copyOf(grantList);
            }
            case GUEST ->
                List.of(
                        new CanPublish(false),
                        new CanPublishData(false),
                        new CanSubscribe(true),
                        new CanUpdateOwnMetadata(true));
        };
    }

    /**
     * Builds the list of allowed track sources based on meeting settings.
     *
     * <p>Source strings follow LiveKit's canonical names:
     * <ul>
     *   <li>"microphone" - audio from microphone</li>
     *   <li>"camera" - video from camera</li>
     *   <li>"screen_share" - screen share video</li>
     *   <li>"screen_share_audio" - screen share audio</li>
     * </ul>
     *
     * @param settings current meeting settings
     * @return list of allowed source strings for PARTICIPANT tokens
     */
    private List<String> buildAllowedSources(MeetingSettings settings) {
        return ParticipantGrants.buildAllowedSources(settings);
    }

    // -------------------------------------------------------------------------
    // Room management
    // -------------------------------------------------------------------------

    @Override
    public Result<Void, MeetingError> updateParticipantPermissions(
            LiveKitRoomName roomName, String identity, ParticipantGrants grants) {
        LivekitModels.ParticipantPermission permission = toPermission(grants);

        try {
            Response<LivekitModels.ParticipantInfo> response = roomServiceClient
                    .updateParticipant(roomName.value(), identity, null, null, permission, null)
                    .execute();

            if (!response.isSuccessful()) {
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn(
                        "Failed to update permissions for participant '{}' in room '{}': {}",
                        identity,
                        roomName.value(),
                        msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info(
                    "Updated permissions for participant '{}' in room '{}': canPublish={}, canPublishData={}, canSubscribe={}",
                    identity,
                    roomName.value(),
                    grants.canPublish(),
                    grants.canPublishData(),
                    grants.canSubscribe());

            return Result.success();

        } catch (IOException e) {
            log.warn(
                    "Network error updating participant '{}' in room '{}': {}",
                    identity,
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> updateParticipantProfile(
            LiveKitRoomName roomName,
            String identity,
            ParticipantRole role,
            String fullName,
            @Nullable String avatarUrl) {
        try {
            Response<LivekitModels.ParticipantInfo> response = roomServiceClient
                    .updateParticipant(
                            roomName.value(),
                            identity,
                            fullName,
                            null,
                            toPermission(toParticipantGrants(role)),
                            buildProfileAttributes(avatarUrl, role))
                    .execute();

            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    log.debug(
                            "Participant '{}' no longer exists in room '{}' during profile sync",
                            identity,
                            roomName.value());
                    return Result.failure(new MeetingError.LiveKitParticipantNotFound(
                            roomName.value(), identity));
                }
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn(
                        "Failed to update profile for participant '{}' in room '{}': {}",
                        identity,
                        roomName.value(),
                        msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info(
                    "Updated profile for participant '{}' in room '{}': fullName='{}'",
                    identity,
                    roomName.value(),
                    fullName);
            return Result.success();

        } catch (IOException e) {
            log.warn(
                    "Network error updating profile for participant '{}' in room '{}': {}",
                    identity,
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    private ParticipantGrants toParticipantGrants(ParticipantRole role) {
        return switch (role) {
            case HOST, PARTICIPANT -> ParticipantGrants.speaker();
            case GUEST -> ParticipantGrants.observer();
        };
    }

    private String recordingFilepath(MeetingId meetingId) {
        return "meetings/%s/recording.mp4".formatted(meetingId.value());
    }

    LivekitModels.ParticipantPermission toPermission(ParticipantGrants grants) {
        LivekitModels.ParticipantPermission.Builder builder =
                LivekitModels.ParticipantPermission.newBuilder()
                        .setCanPublish(grants.canPublish())
                        .setCanPublishData(grants.canPublishData())
                        .setCanSubscribe(grants.canSubscribe());
        grants.allowedSources().stream()
                .map(this::toTrackSource)
                .forEach(builder::addCanPublishSources);
        return builder.build();
    }

    private LivekitModels.TrackSource toTrackSource(String source) {
        return switch (source) {
            case "microphone" -> LivekitModels.TrackSource.MICROPHONE;
            case "camera" -> LivekitModels.TrackSource.CAMERA;
            case "screen_share" -> LivekitModels.TrackSource.SCREEN_SHARE;
            case "screen_share_audio" -> LivekitModels.TrackSource.SCREEN_SHARE_AUDIO;
            default ->
                throw new IllegalArgumentException("Unsupported LiveKit track source: " + source);
        };
    }

    private Map<String, String> buildProfileAttributes(
            @Nullable String avatarUrl, ParticipantRole role) {
        Map<String, String> attributes =
                new LinkedHashMap<>(new ParticipantAttributes(null, role).toMap());
        attributes.put("avatarUrl", avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl : "");
        return Map.copyOf(attributes);
    }

    @Override
    public Result<Void, MeetingError> deleteRoom(LiveKitRoomName roomName) {
        try {
            Response<?> response =
                    roomServiceClient.deleteRoom(roomName.value()).execute();

            if (!response.isSuccessful()) {
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn("Failed to delete LiveKit room '{}': {}", roomName.value(), msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info("Deleted LiveKit room: {}", roomName.value());
            return Result.success();

        } catch (IOException e) {
            log.warn("Network error deleting room '{}': {}", roomName.value(), e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> removeParticipant(LiveKitRoomName roomName, String identity) {
        try {
            Response<?> response = roomServiceClient
                    .removeParticipant(roomName.value(), identity)
                    .execute();

            if (response.isSuccessful()) {
                log.info("Removed participant '{}' from room '{}'", identity, roomName.value());
                return Result.success();
            }

            if (response.code() == 404) {
                log.debug(
                        "Participant '{}' not found in room '{}' during kick (already gone)",
                        identity,
                        roomName.value());
                return Result.success();
            }

            String msg = "HTTP %d: %s".formatted(response.code(), response.message());
            log.warn(
                    "Failed to remove participant '{}' from room '{}': {}",
                    identity,
                    roomName.value(),
                    msg);
            return Result.failure(new MeetingError.LiveKitUnavailable(msg));

        } catch (IOException e) {
            log.warn(
                    "Network error removing participant '{}' from room '{}': {}",
                    identity,
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> muteParticipantTrack(
            LiveKitRoomName roomName, String identity, String source) {
        try {
            Response<LivekitModels.ParticipantInfo> participantResponse =
                    roomServiceClient.getParticipant(roomName.value(), identity).execute();

            if (!participantResponse.isSuccessful() || participantResponse.body() == null) {
                if (participantResponse.code() == 404) {
                    return Result.failure(new MeetingError.LiveKitParticipantNotFound(
                            roomName.value(), identity));
                }
                String msg = "HTTP %d: %s"
                        .formatted(participantResponse.code(), participantResponse.message());
                log.warn(
                        "Failed to get participant '{}' in room '{}': {}",
                        identity,
                        roomName.value(),
                        msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            LivekitModels.ParticipantInfo participantInfo = participantResponse.body();
            String trackSid = resolveTrackSid(participantInfo, source);
            if (trackSid == null) {
                return Result.failure(new MeetingError.TrackNotFound(identity, source));
            }

            Response<LivekitModels.TrackInfo> muteResponse = roomServiceClient
                    .mutePublishedTrack(roomName.value(), identity, trackSid, true)
                    .execute();

            if (!muteResponse.isSuccessful()) {
                String msg = "HTTP %d: %s".formatted(muteResponse.code(), muteResponse.message());
                log.warn(
                        "Failed to mute track '{}' for participant '{}' in room '{}': {}",
                        trackSid,
                        identity,
                        roomName.value(),
                        msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info(
                    "Muted {} track (sid={}) for participant '{}' in room '{}'",
                    source,
                    trackSid,
                    identity,
                    roomName.value());
            return Result.success();

        } catch (IOException e) {
            log.warn(
                    "Network error muting {} track for participant '{}' in room '{}': {}",
                    source,
                    identity,
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> muteAllParticipantMicTracks(
            LiveKitRoomName roomName, List<String> identities) {
        if (identities.isEmpty()) {
            return Result.success();
        }

        boolean atLeastOneSucceeded = false;
        MeetingError.LiveKitUnavailable lastFailure = null;

        for (String identity : identities) {
            var result = muteParticipantTrack(roomName, identity, "microphone");
            if (result instanceof Result.Success<?, ?>) {
                atLeastOneSucceeded = true;
            } else if (result instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
                if (error instanceof MeetingError.LiveKitParticipantNotFound) {
                    log.debug(
                            "Skipping mute for participant '{}' — already left room '{}'",
                            identity,
                            roomName.value());
                    atLeastOneSucceeded = true;
                } else if (error instanceof MeetingError.TrackNotFound) {
                    log.debug(
                            "Skipping mute for participant '{}' — no microphone track in room '{}'",
                            identity,
                            roomName.value());
                    atLeastOneSucceeded = true;
                } else if (error instanceof MeetingError.LiveKitUnavailable unavailable) {
                    lastFailure = unavailable;
                }
            }
        }

        if (!atLeastOneSucceeded && lastFailure != null) {
            return Result.failure(lastFailure);
        }

        return Result.success();
    }

    private String resolveTrackSid(LivekitModels.ParticipantInfo info, String source) {
        LivekitModels.TrackSource targetSource =
                switch (source) {
                    case "microphone" -> LivekitModels.TrackSource.MICROPHONE;
                    case "camera" -> LivekitModels.TrackSource.CAMERA;
                    case "screen_share" -> LivekitModels.TrackSource.SCREEN_SHARE;
                    default -> null;
                };
        if (targetSource == null) {
            return null;
        }
        for (LivekitModels.TrackInfo track : info.getTracksList()) {
            if (track.getSource() == targetSource) {
                return track.getSid();
            }
        }
        return null;
    }

    @Override
    public Result<Void, MeetingError> updateRoomMetadata(
            LiveKitRoomName roomName, String metadata) {
        try {
            Response<LivekitModels.Room> response = roomServiceClient
                    .updateRoomMetadata(roomName.value(), metadata)
                    .execute();

            if (!response.isSuccessful()) {
                String msg = "HTTP %d: %s".formatted(response.code(), response.message());
                log.warn("Failed to update room metadata for room '{}': {}", roomName.value(), msg);
                return Result.failure(new MeetingError.LiveKitUnavailable(msg));
            }

            log.info("Updated room metadata for room '{}': {}", roomName.value(), metadata);
            return Result.success();

        } catch (IOException e) {
            log.warn(
                    "Network error updating room metadata for room '{}': {}",
                    roomName.value(),
                    e.getMessage());
            return Result.failure(new MeetingError.LiveKitUnavailable(e.getMessage()));
        }
    }
}
