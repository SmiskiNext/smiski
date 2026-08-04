package io.github.smiskinext.shared.infrastructure.outbox;

import org.springframework.scheduling.annotation.Scheduled;

public class OutboxRelayTrigger {

    private final OutboxRelay outboxRelay;

    public OutboxRelayTrigger(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay:PT5S}")
    public void trigger() {
        outboxRelay.relay();
    }
}
