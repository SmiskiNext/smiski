package io.github.smiskinext.meetingmanagement.infrastructure.grpc;

import io.grpc.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * gRPC {@link ClientInterceptor} that propagates the authenticated user's ID
 * from the Spring {@link SecurityContextHolder} into outbound gRPC call metadata.
 *
 * <p>The {@code x-user-id} metadata key mirrors the {@code X-User-ID} HTTP header
 * convention used by Kong, keeping the identity propagation model consistent
 * across transport boundaries.
 */
@Component
public class AuthMetadataInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof String userId) {
                    headers.put(USER_ID_KEY, userId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
