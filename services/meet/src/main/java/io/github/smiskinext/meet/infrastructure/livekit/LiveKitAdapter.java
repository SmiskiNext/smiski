package io.github.smiskinext.meet.infrastructure.livekit;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.shared.domain.Result;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanUpdateOwnMetadata;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import livekit.LivekitModels;
import livekit.LivekitModels.ParticipantPermission;
import livekit.LivekitModels.TrackSource;
import livekit.LivekitRoom.RoomConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class LiveKitAdapter implements LiveKitPort {

    private static final Logger log = LoggerFactory.getLogger(LiveKitAdapter.class);

    private static final Map<String, TrackSource> TRACK_SOURCE_BY_GRANT_NAME = Map.of(
            "microphone", TrackSource.MICROPHONE,
            "camera", TrackSource.CAMERA,
            "screen_share", TrackSource.SCREEN_SHARE,
            "screen_share_audio", TrackSource.SCREEN_SHARE_AUDIO);

    private final LiveKitProperties properties;
    private final RoomServiceClient roomServiceClient;

    public LiveKitAdapter(LiveKitProperties properties, RoomServiceClient roomServiceClient) {
        this.properties = properties;
        this.roomServiceClient = roomServiceClient;
    }

    @Override
    public Result<String, MeetingError> generateToken(LiveKitTokenRequest request) {
        try {
            AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
            token.setName(request.displayName());
            token.setIdentity(request.identity().value());
            token.setTtl(properties.tokenExpirySeconds() * 1000L);
            token.getAttributes().putAll(request.participantAttributes().toMap());
            token.setRoomConfiguration(RoomConfiguration.newBuilder()
                    .setName(request.roomName().value())
                    .setMetadata(request.tenantId())
                    .build());

            boolean isHost = request.role() == ParticipantRole.HOST;

            token.addGrants(new RoomJoin(true), new RoomName(request.roomName().value()));

            if (isHost) {
                token.addGrants(
                        new RoomAdmin(true),
                        new CanPublish(true),
                        new CanPublishData(true),
                        new CanSubscribe(true),
                        new CanUpdateOwnMetadata(true));
            } else {
                MeetingSettings settings = request.meetingSettings();
                ParticipantGrants grants = ParticipantGrants.fromSettings(settings, request.role());
                List<String> allowedSources = settings == null
                        ? List.of()
                        : ParticipantGrants.buildAllowedSources(settings);
                token.addGrants(
                        new CanPublish(grants.canPublish() && !allowedSources.isEmpty()),
                        new CanPublishData(grants.canPublishData()),
                        new CanSubscribe(grants.canSubscribe()),
                        new CanUpdateOwnMetadata(true));
                if (!allowedSources.isEmpty()) {
                    token.addGrants(new CanPublishSources(allowedSources));
                }
            }

            return Result.success(token.toJwt());
        } catch (Exception e) {
            return Result.failure(new MeetingError.LiveKitUnavailable(
                    "Token generation failed: " + e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> updateParticipantPermissions(
            LiveKitRoomName roomName, String identity, ParticipantGrants grants) {
        try {
            boolean currentCanPublishData = resolveCurrentCanPublishData(roomName, identity);

            List<TrackSource> trackSources = grants.allowedSources().stream()
                    .map(TRACK_SOURCE_BY_GRANT_NAME::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            ParticipantPermission.Builder permissionBuilder = ParticipantPermission.newBuilder()
                    .setCanPublish(grants.canPublish())
                    .setCanPublishData(currentCanPublishData)
                    .setCanSubscribe(grants.canSubscribe())
                    .addAllCanPublishSources(trackSources);

            Response<LivekitModels.ParticipantInfo> response = roomServiceClient
                    .updateParticipant(
                            roomName.value(), identity, null, null, permissionBuilder.build(), null)
                    .execute();
            if (!response.isSuccessful()) {
                return Result.failure(
                        new MeetingError.LiveKitUnavailable("Permission update failed: HTTP "
                                + response.code() + " for identity " + identity));
            }
            return Result.success();
        } catch (IOException e) {
            return Result.failure(new MeetingError.LiveKitUnavailable(
                    "Permission update failed: " + e.getMessage()));
        }
    }

    private boolean resolveCurrentCanPublishData(LiveKitRoomName roomName, String identity) {
        try {
            Response<List<LivekitModels.ParticipantInfo>> listResponse =
                    roomServiceClient.listParticipants(roomName.value()).execute();
            if (listResponse.isSuccessful() && listResponse.body() != null) {
                for (LivekitModels.ParticipantInfo info : listResponse.body()) {
                    if (info.getIdentity().equals(identity) && info.hasPermission()) {
                        return info.getPermission().getCanPublishData();
                    }
                }
            }
        } catch (IOException e) {
            log.warn(
                    "Failed to read current canPublishData for identity={}, defaulting to true",
                    identity,
                    e);
        }
        return true;
    }

    @Override
    public Result<Void, MeetingError> updateParticipantProfile(
            LiveKitRoomName roomName,
            String identity,
            ParticipantRole role,
            String fullName,
            @Nullable String avatarUrl) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public Result<Void, MeetingError> deleteRoom(LiveKitRoomName roomName) {
        try {
            Response<Void> response =
                    roomServiceClient.deleteRoom(roomName.value()).execute();
            if (!response.isSuccessful()) {
                return Result.failure(
                        new MeetingError.LiveKitUnavailable("Room deletion failed: HTTP "
                                + response.code() + " for room " + roomName.value()));
            }
            return Result.success();
        } catch (IOException e) {
            return Result.failure(
                    new MeetingError.LiveKitUnavailable("Room deletion failed: " + e.getMessage()));
        }
    }

    @Override
    public Result<Void, MeetingError> removeParticipant(LiveKitRoomName roomName, String identity) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public Result<Void, MeetingError> muteParticipantTrack(
            LiveKitRoomName roomName, String identity, String source) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public Result<Void, MeetingError> muteAllParticipantMicTracks(
            LiveKitRoomName roomName, List<String> identities) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }

    @Override
    public Result<Void, MeetingError> updateRoomMetadata(
            LiveKitRoomName roomName, String metadata) {
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
    }
}
