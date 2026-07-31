package io.github.smiskinext.meet.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PermissionEnforcementControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-permission-test";
    private static final String HOST_ID = "permission-test-host";
    private static final String OTHER_ACCOUNT_ID = "permission-test-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureTenantExists() {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);
    }

    @Test
    void getMeetingWithNoPermissionsReturns403NotAuthorized() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(get("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void createInstantWithOnlyViewMeetingPermissionReturns403NotAuthorized() throws Exception {
        String requestBody = buildInstantMeetingBody();

        mockMvc.perform(post("/api/1/meetings:instant")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void updateMeetingWithEditMeetingPermissionButNonHostReturns403Forbidden() throws Exception {
        UUID meetingId = createScheduledMeeting();

        Instant start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String updateBody = """
                {"title":"Updated Title","description":"Updated desc",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC",
                "organizerEmail":"other@example.com","organizerDisplayName":"Other User"}
                """.formatted(start, start.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", OTHER_ACCOUNT_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void acceptInvitationWithNoPermissionsIsNotRejectedByPermissionCheck() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                meetingId,
                                inviteeId)
                        .header("X-Account-Id", OTHER_ACCOUNT_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403);
                });
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Permission Test Meeting","description":"Test",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"MANUAL_APPROVAL","maxParticipants":50,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC",
                "organizerEmail":"host@example.com","organizerDisplayName":"Host User"}
                """.formatted(start, start.plus(1, ChronoUnit.HOURS));
        MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id"));
    }

    private String buildInstantMeetingBody() {
        return """
                {
                    "title": "Instant Test Meeting",
                    "description": "Permission test",
                    "issueLink": {
                        "issueId": "10001",
                        "issueKey": "PROJ-1",
                        "projectKey": "PROJ"
                    },
                    "settings": {
                        "admissionPolicy": "ALLOW_ALL",
                        "maxParticipants": 50,
                        "allowScreenShare": true,
                        "chatEnabled": true,
                        "allowMicrophone": true,
                        "allowVideo": true
                    },
                    "host": {
                        "displayName": "Host",
                        "deviceId": "device-1"
                    },
                    "organizerEmail": "host@example.com",
                    "organizerDisplayName": "Host User",
                    "zoneId": "UTC"
                }
                """;
    }
}
