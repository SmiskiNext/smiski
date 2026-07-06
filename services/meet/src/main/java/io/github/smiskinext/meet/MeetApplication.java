package io.github.smiskinext.meet;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {"io.github.smiskinext.meet", "io.github.smiskinext.shared"})
@OpenAPIDefinition(info = @Info(title = "Meet", version = "1.0.0"))
@EnableScheduling
public class MeetApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetApplication.class, args);
    }
}
