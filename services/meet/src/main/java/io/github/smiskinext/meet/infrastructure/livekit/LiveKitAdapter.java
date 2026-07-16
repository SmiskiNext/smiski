package io.github.smiskinext.meet.infrastructure.livekit;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.shared.domain.Result;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanUpdateOwnMetadata;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class LiveKitAdapter implements LiveKitPort {

    private final LiveKitProperties properties;

    public LiveKitAdapter(LiveKitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Result<String, MeetingError> generateToken(LiveKitTokenRequest request) {
        try {
            AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
            token.setName(request.displayName());
            token.setIdentity(request.identity().value());
            token.setTtl(properties.tokenExpirySeconds() * 1000L);
            token.getAttributes().putAll(request.participantAttributes().toMap());

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
                ParticipantGrants grants =
                        ParticipantGrants.fromSettings(request.meetingSettings(), request.role());
                token.addGrants(
                        new CanPublish(grants.canPublish()),
                        new CanPublishData(grants.canPublishData()),
                        new CanSubscribe(grants.canSubscribe()),
                        new CanUpdateOwnMetadata(true));
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
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
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
        throw new UnsupportedOperationException("Not implemented in create-instant-meeting slice");
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
