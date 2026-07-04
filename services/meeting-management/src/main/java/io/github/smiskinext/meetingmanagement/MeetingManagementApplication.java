package io.github.smiskinext.meetingmanagement;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {
            "io.github.phunguy65.zms.meetingmanagement",
            "io.github.phunguy65.zms.shared"
        })
@OpenAPIDefinition(info = @Info(title = "MeetingManagement", version = "1.0.0"))
@EnableScheduling
public class MeetingManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingManagementApplication.class, args);
    }
}
