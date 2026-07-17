package io.github.smiskinext.meet.infrastructure.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Invite token settings bound from the {@code app.invite} prefix.
 *
 * @param tokenExpiryDays days until an issued invite token expires; must be positive
 */
@Validated
@ConfigurationProperties(prefix = "app.invite")
public record InviteProperties(@Positive int tokenExpiryDays) {}
