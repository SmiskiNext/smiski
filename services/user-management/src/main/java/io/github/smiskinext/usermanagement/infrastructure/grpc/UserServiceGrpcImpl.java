package io.github.smiskinext.usermanagement.infrastructure.grpc;

import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserByIdRequest;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserByIdResponse;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserRequest;
import io.github.phunguy65.zms.proto.user.v1.BatchGetUserResponse;
import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import io.github.phunguy65.zms.proto.user.v1.UserSnapshot;
import io.github.smiskinext.usermanagement.application.usecase.internal.BatchGetUserByIdUseCase;
import io.github.smiskinext.usermanagement.application.usecase.internal.BatchGetUserUseCase;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * gRPC server implementation for the UserService.
 *
 * <p>Delegates to {@link BatchGetUserUseCase} for user resolution.
 * Deleted users are excluded. Partial results are returned — missing emails are absent
 * from the response map.
 */
@Component
public class UserServiceGrpcImpl extends UserServiceGrpc.UserServiceImplBase {

    private final BatchGetUserUseCase batchGetUserUseCase;
    private final BatchGetUserByIdUseCase batchGetUserByIdUseCase;

    public UserServiceGrpcImpl(
            BatchGetUserUseCase batchGetUserUseCase,
            BatchGetUserByIdUseCase batchGetUserByIdUseCase) {
        this.batchGetUserUseCase = batchGetUserUseCase;
        this.batchGetUserByIdUseCase = batchGetUserByIdUseCase;
    }

    @Override
    public void batchGetUser(
            BatchGetUserRequest request, StreamObserver<BatchGetUserResponse> responseObserver) {
        var users = batchGetUserUseCase.execute(request.getEmailsList());

        var responseBuilder = BatchGetUserResponse.newBuilder();
        users.forEach((email, user) -> responseBuilder.putUsers(email, toSnapshot(user)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void batchGetUserById(
            BatchGetUserByIdRequest request,
            StreamObserver<BatchGetUserByIdResponse> responseObserver) {
        var users = batchGetUserByIdUseCase.execute(request.getUserIdsList());

        var responseBuilder = BatchGetUserByIdResponse.newBuilder();
        users.forEach(
                (userId, user) -> responseBuilder.putUsers(userId.toString(), toSnapshot(user)));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private UserSnapshot toSnapshot(UserSummary summary) {
        var builder = UserSnapshot.newBuilder()
                .setId(summary.id().toString())
                .setEmail(summary.email())
                .setFullName(summary.fullName())
                .setAuthProvider(summary.authProvider())
                .setCreatedAt(toTimestamp(summary.createdAt()))
                .setUpdatedAt(toTimestamp(summary.updatedAt()));

        if (summary.username() != null) {
            builder.setUsername(StringValue.of(summary.username()));
        }
        if (summary.avatarUrl() != null) {
            builder.setAvatarUrl(StringValue.of(summary.avatarUrl()));
        }
        if (summary.preferences() != null) {
            parsePreferencesToStruct(summary.preferences()).ifPresent(builder::setPreferences);
        }

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Parses a raw JSON string into a protobuf {@link Struct}.
     * Returns empty if the JSON is invalid or not an object.
     */
    private Optional<Struct> parsePreferencesToStruct(String json) {
        try {
            var structBuilder = Struct.newBuilder();
            com.google.protobuf.util.JsonFormat.parser()
                    .ignoringUnknownFields()
                    .merge(json, structBuilder);
            return Optional.of(structBuilder.build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
