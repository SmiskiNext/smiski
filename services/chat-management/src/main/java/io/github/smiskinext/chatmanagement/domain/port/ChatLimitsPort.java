package io.github.smiskinext.chatmanagement.domain.port;

/**
 * Outbound port for reading chat limit configuration.
 *
 * <p>Implemented by {@code ChatLimitsConfig} in the infrastructure layer.
 */
public interface ChatLimitsPort {

    /**
     * Maximum message content length in characters.
     */
    int getMaxMessageLength();
}
