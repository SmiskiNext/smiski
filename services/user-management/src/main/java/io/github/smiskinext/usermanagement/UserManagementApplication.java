package io.github.smiskinext.usermanagement;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {
            "io.github.phunguy65.zms.usermanagement",
            "io.github.phunguy65.zms.shared"
        })
@OpenAPIDefinition(info = @Info(title = "UserManagement", version = "1.0.0"))
@EnableScheduling
@EntityScan(basePackages = {"io.github.phunguy65.zms.usermanagement.infrastructure.persistence"})
public class UserManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserManagementApplication.class, args);
    }
}
