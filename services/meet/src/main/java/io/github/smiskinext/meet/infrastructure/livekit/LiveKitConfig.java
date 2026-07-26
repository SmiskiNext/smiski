package io.github.smiskinext.meet.infrastructure.livekit;

import io.livekit.server.WebhookReceiver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitConfig {

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(LiveKitProperties properties) {
        return new WebhookReceiver(properties.apiKey(), properties.apiSecret());
    }
}
