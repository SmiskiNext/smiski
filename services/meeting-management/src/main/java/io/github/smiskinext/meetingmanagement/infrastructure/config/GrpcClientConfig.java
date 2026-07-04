package io.github.smiskinext.meetingmanagement.infrastructure.config;

import io.github.smiskinext.meetingmanagement.infrastructure.grpc.AuthMetadataInterceptor;
import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
class GrpcClientConfig {

    @Bean
    UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub(
            GrpcChannelFactory channels, AuthMetadataInterceptor authMetadataInterceptor) {
        return UserServiceGrpc.newBlockingStub(channels.createChannel("user-management"))
                .withInterceptors(authMetadataInterceptor);
    }
}
