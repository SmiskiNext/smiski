package io.github.smiskinext.record;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {"io.github.smiskinext.record", "io.github.smiskinext.shared"})
@OpenAPIDefinition(info = @Info(title = "Record", version = "1.0.0"))
@EnableScheduling
public class RecordApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecordApplication.class, args);
    }
}
