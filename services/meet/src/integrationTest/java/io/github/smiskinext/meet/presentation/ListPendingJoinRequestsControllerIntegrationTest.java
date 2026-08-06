package io.github.smiskinext.meet.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
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
class ListPendingJoinRequestsControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-test";
    private static final String HOST_ID = "host";

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
    void hostRetrievesPendingListReturns200WithCorrectShape() throws Exception {
        UUID meetingId = insertMeeting();
        createPendingRequest(meetingId, "device-1");
        createPendingRequest(meetingId, "device-2");
        createPendingRequest(meetingId, "device-3");

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].requestId").isNotEmpty())
                .andExpect(jsonPath("$.results[0].accountId").isNotEmpty())
                .andExpect(jsonPath("$.results[0].displayName").isNotEmpty())
                .andExpect(jsonPath("$.results[0].status").value("PENDING"))
                .andExpect(jsonPath("$.results[0].requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.results[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.pageSize").value(20));
    }

    @Test
    void nonHostCallerReturns403NotOwner() throws Exception {
        UUID meetingId = insertMeeting();

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "not-the-host")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
    }

    @Test
    void unknownMeetingReturns404MeetingNotFound() throws Exception {
        UUID unknownMeeting = UUID.randomUUID();

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", unknownMeeting)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void missingAccountHeaderReturns400ValidationError() throws Exception {
        UUID meetingId = insertMeeting();

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pageSizeZeroReturns400AndPageSize101Returns400() throws Exception {
        UUID meetingId = insertMeeting();

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", meetingId)
                        .param("pageSize", "0")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/1/meetings/{id}/join-requests", meetingId)
                        .param("pageSize", "101")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void createPendingRequest(UUID meetingId, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "participant-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"displayName\": \"Alice\", \"deviceId\": \"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
    }

    private UUID insertMeeting() {
        UUID id = UUID.randomUUID();
        String settings = """
                {"admissionPolicy": "MANUAL_APPROVAL", "maxParticipants": 50, \
                "allowScreenShare": true, "chatEnabled": true, "allowMicrophone": true, \
                "allowVideo": true}
                """;
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, ?, 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Title', 'Description', 'UTC',
                    'INSTANT', 'RUNNING', ?::jsonb)
                """,
                TENANT_ID,
                id,
                HOST_ID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                settings);
        return id;
    }
}
