package io.github.smiskinext.tenant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {"io.github.smiskinext.tenant", "io.github.smiskinext.shared"})
@OpenAPIDefinition(info = @Info(title = "Tenant", version = "1.0.0"))
public class TenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantApplication.class, args);
    }
}
