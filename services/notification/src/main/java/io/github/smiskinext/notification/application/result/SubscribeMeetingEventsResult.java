package io.github.smiskinext.notification.application.result;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Result returned by {@code SubscribeMeetingEventsUseCase} carrying the registered SSE emitter.
 *
 * @param emitter the Spring MVC emitter the controller returns to the client
 */
public record SubscribeMeetingEventsResult(SseEmitter emitter) {}
