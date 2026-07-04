package io.github.smiskinext.notification.infrastructure.grpc;

import io.github.smiskinext.notification.domain.port.UserLookupPort;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserByIdRequest;
import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import io.github.phunguy65.zms.proto.user.v1.UserSnapshot;
import io.grpc.StatusRuntimeException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class UserServiceGrpcLookupAdapter implements UserLookupPort {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public UserServiceGrpcLookupAdapter(UserServiceGrpc.UserServiceBlockingStub userServiceStub) {
        this.userServiceStub = userServiceStub;
    }

    @Override
    public Map<UUID, UserInfo> findUsersByIds(List<UUID> userIds) {
        BatchGetUserByIdRequest request = BatchGetUserByIdRequest.newBuilder()
                .addAllUserIds(userIds.stream().map(UUID::toString).toList())
                .build();
        try {
            return toUserInfoMap(userServiceStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .batchGetUserById(request)
                    .getUsersMap());
        } catch (StatusRuntimeException exception) {
            throw new UserLookupException(exception);
        }
    }

    private Map<UUID, UserInfo> toUserInfoMap(Map<String, UserSnapshot> snapshots) {
        Map<UUID, UserInfo> users = new HashMap<>();
        for (Map.Entry<String, UserSnapshot> entry : snapshots.entrySet()) {
            UserSnapshot snapshot = entry.getValue();
            users.put(
                    UUID.fromString(entry.getKey()),
                    new UserInfo(
                            UUID.fromString(snapshot.getId()),
                            snapshot.getEmail(),
                            snapshot.getFullName()));
        }
        return users;
    }

    public static class UserLookupException extends RuntimeException {

        public UserLookupException(Throwable cause) {
            super("User lookup failed", cause);
        }
    }
}
