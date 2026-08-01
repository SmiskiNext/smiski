package io.github.smiskinext.notification.application;

import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Application-layer port for registering SSE subscription emitters.
 *
 * <p>Lives at the application layer (not domain) because {@link SseEmitter} is a Spring MVC type.
 * Implemented by {@code SseConnectionManager} in the infrastructure layer.
 */
public interface SseSubscriptionPort {

    /**
     * Registers a new host emitter for a meeting and returns it to the caller.
     *
     * @param meetingId the meeting to subscribe to
     * @return the registered emitter
     */
    SseEmitter subscribe(UUID meetingId);

    /**
     * Registers a new requester emitter for a join request and returns it to the caller.
     *
     * @param requestId the join request whose outcome to subscribe to
     * @return the registered emitter
     */
    SseEmitter subscribeRequest(UUID requestId);
}
