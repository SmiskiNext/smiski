package io.github.smiskinext.chatmanagement.infrastructure.config;

import io.github.smiskinext.chatmanagement.domain.port.ChatLimitsPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chat limits configuration loaded from environment variables or application.yaml.
 *
 * <p>Changes require service restart.
 *
 * <p>Default values ensure the service starts safely in local development.
 */
@Component
@ConfigurationProperties(prefix = "app.chat.limits")
public class ChatLimitsConfig implements ChatLimitsPort {

    /**
     * Maximum message content length in characters. Default: 4000.
     */
    private int maxMessageLength = 4000;

    @Override
    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }
}
