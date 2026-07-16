package io.github.smiskinext.meet.domain.port;

/**
 * Settings a {@link ShortCodeGenerator} needs to draw random join codes.
 */
public interface ShortCodeGenerationSettings {

    /** Characters codes are drawn from. */
    String alphabet();

    /** Number of characters per generated code. */
    int length();
}
