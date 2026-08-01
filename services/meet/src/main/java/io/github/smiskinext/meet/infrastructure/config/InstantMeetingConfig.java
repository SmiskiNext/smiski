package io.github.smiskinext.meet.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InstantMeetingProperties.class)
public class InstantMeetingConfig {}
