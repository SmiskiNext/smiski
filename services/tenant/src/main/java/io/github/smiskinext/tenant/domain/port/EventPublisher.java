package io.github.smiskinext.tenant.domain.port;

import io.github.smiskinext.tenant.domain.event.PublishableEvent;

public interface EventPublisher {

    void publish(PublishableEvent event);
}
