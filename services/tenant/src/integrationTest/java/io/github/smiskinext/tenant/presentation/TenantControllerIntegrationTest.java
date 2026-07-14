package io.github.smiskinext.tenant.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.smiskinext.tenant.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class TenantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void first_install_returns_201_with_location() throws Exception {
        String body = """
                {
                    "id": "install-create-1",
                    "app": {
                        "id": "app-1",
                        "version": "1.0.0",
                        "name": "My App"
                    }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/1/tenants")
                        .header("X-Tenant-ID", "cloud-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.tenantId").value("cloud-create-1"))
                .andExpect(jsonPath("$.installationId").value("install-create-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).contains("/tenants/cloud-create-1");
    }

    @Test
    void redelivery_returns_200() throws Exception {
        String body = """
                {
                    "id": "install-redeliver-1",
                    "app": {
                        "id": "app-1",
                        "version": "1.0.0"
                    }
                }
                """;

        mockMvc.perform(post("/api/1/tenants")
                        .header("X-Tenant-ID", "cloud-redeliver-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {
                    "id": "install-redeliver-2",
                    "app": {
                        "id": "app-1",
                        "version": "2.0.0"
                    }
                }
                """;

        mockMvc.perform(post("/api/1/tenants")
                        .header("X-Tenant-ID", "cloud-redeliver-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value("install-redeliver-2"));
    }

    @Test
    void missing_tenant_id_returns_problem_details() throws Exception {
        String body = """
                {
                    "id": "install-1",
                    "app": {
                        "id": "app-1",
                        "version": "1.0.0"
                    }
                }
                """;

        mockMvc.perform(post("/api/1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_CONTEXT"));
    }

    @Test
    void missing_id_returns_validation_error() throws Exception {
        String body = """
                {
                    "app": {
                        "id": "app-1",
                        "version": "1.0.0"
                    }
                }
                """;

        mockMvc.perform(post("/api/1/tenants")
                        .header("X-Tenant-ID", "cloud-valid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'id' && @.code == 'REQUIRED')]")
                        .exists());
    }

    @Test
    void missing_app_id_returns_validation_error() throws Exception {
        String body = """
                {
                    "id": "install-1",
                    "app": {
                        "version": "1.0.0"
                    }
                }
                """;

        mockMvc.perform(post("/api/1/tenants")
                        .header("X-Tenant-ID", "cloud-valid-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'app.id' && @.code == 'REQUIRED')]")
                        .exists());
    }
}
