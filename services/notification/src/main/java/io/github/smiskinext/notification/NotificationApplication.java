package io.github.smiskinext.notification;

import io.github.smiskinext.notification.application.sse.SseProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
        excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@EnableConfigurationProperties(SseProperties.class)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
