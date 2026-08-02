package io.github.smiskinext.notification;

import io.github.smiskinext.notification.infrastructure.config.SseProperties;
import io.github.smiskinext.notification.infrastructure.email.EmailProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SseProperties.class, EmailProperties.class})
@OpenAPIDefinition(info = @Info(title = "Notification", version = "1.0.0"))
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
