package io.github.smiskinext.notification.infrastructure.config;

import io.github.phunguy65.zms.proto.user.v1.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
class GrpcClientConfig {

    @Bean
    UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub(GrpcChannelFactory channels) {
        return UserServiceGrpc.newBlockingStub(channels.createChannel("user-management"));
    }
}
