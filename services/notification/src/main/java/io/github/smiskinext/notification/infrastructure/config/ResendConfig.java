package io.github.smiskinext.notification.infrastructure.config;

import com.resend.Resend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResendConfig {

    @Bean
    public Resend resend(NotificationProperties notificationProperties) {
        notificationProperties.validateRequiredValues();
        return new Resend(notificationProperties.getResend().getApiKey());
    }
}
