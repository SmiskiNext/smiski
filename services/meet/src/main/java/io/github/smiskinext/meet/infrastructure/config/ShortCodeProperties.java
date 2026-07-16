package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.domain.port.ShortCodeAllocationSettings;
import io.github.smiskinext.meet.domain.port.ShortCodeGenerationSettings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Short-code settings bound from the {@code app.short-code} prefix.
 *
 * <p>Implements the domain contracts so generator and allocator depend only on the settings they
 * need, never on this binding type or Spring.
 *
 * @param alphabet characters codes are drawn from; must not be blank
 * @param length number of characters per generated code; must be positive
 * @param maxAttempts maximum unique-code allocation attempts before giving up; must be positive
 */
@Validated
@ConfigurationProperties(prefix = "app.short-code")
public record ShortCodeProperties(
        @NotBlank String alphabet,
        @Positive int length,
        @Positive int maxAttempts)
        implements ShortCodeGenerationSettings, ShortCodeAllocationSettings {}
