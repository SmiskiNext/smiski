package io.github.smiskinext.meetingmanagement.infrastructure.grpc;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserByIdRequest;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserRequest;
import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import io.github.phunguy65.zms.proto.user.v1.UserSnapshot;
import io.grpc.StatusRuntimeException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * gRPC client adapter implementing {@link UserGrpcServicePort}.
 *
 * <p>Calls the {@code user-management} gRPC server with a 2-second deadline.
 * Maps gRPC {@code UNAVAILABLE} and {@code DEADLINE_EXCEEDED} status codes to
 * {@link UserGrpcServicePort.UserServiceException}.
 */
@Component
public class UserServiceGrpcAdapter implements UserGrpcServicePort {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public UserServiceGrpcAdapter(UserServiceGrpc.UserServiceBlockingStub userServiceStub) {
        this.userServiceStub = userServiceStub;
    }

    @Override
    public Map<String, ResolvedUser> resolveUsers(List<String> emails) {
        var request = BatchGetUserRequest.newBuilder().addAllEmails(emails).build();

        io.github.phunguy65.zms.proto.user.v1.BatchGetUserResponse response;
        try {
            response = userServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS).batchGetUser(request);
        } catch (StatusRuntimeException e) {
            throw new UserServiceException(new MeetingError.UserServiceUnavailable(
                    e.getStatus().getCode() + ": " + e.getStatus().getDescription()));
        }

        Map<String, ResolvedUser> result = new HashMap<>();
        for (Map.Entry<String, UserSnapshot> entry : response.getUsersMap().entrySet()) {
            result.put(entry.getKey(), toResolvedUser(entry.getValue()));
        }
        return result;
    }

    @Override
    public Map<UUID, ResolvedUser> batchGetUsersByIds(List<UUID> userIds) {
        var request = BatchGetUserByIdRequest.newBuilder()
                .addAllUserIds(userIds.stream().map(UUID::toString).toList())
                .build();

        io.github.phunguy65.zms.proto.user.v1.BatchGetUserByIdResponse response;
        try {
            response = userServiceStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .batchGetUserById(request);
        } catch (StatusRuntimeException e) {
            throw new UserServiceException(new MeetingError.UserServiceUnavailable(
                    e.getStatus().getCode() + ": " + e.getStatus().getDescription()));
        }

        Map<UUID, ResolvedUser> result = new HashMap<>();
        for (Map.Entry<String, UserSnapshot> entry : response.getUsersMap().entrySet()) {
            result.put(UUID.fromString(entry.getKey()), toResolvedUser(entry.getValue()));
        }
        return result;
    }

    private ResolvedUser toResolvedUser(UserSnapshot snapshot) {
        return new ResolvedUser(
                UUID.fromString(snapshot.getId()),
                snapshot.getEmail(),
                snapshot.getFullName(),
                snapshot.hasUsername() ? snapshot.getUsername().getValue() : null,
                snapshot.hasAvatarUrl() ? snapshot.getAvatarUrl().getValue() : null,
                snapshot.getAuthProvider());
    }
}
