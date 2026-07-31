package io.github.smiskinext.meet.presentation;

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
class HandleJoinRequestsControllerIntegrationTest {

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
    void acceptPendingRequestReturns200Approved() throws Exception {
        UUID meetingId = insertMeeting(50);
        UUID requestId = createPendingRequest(meetingId, "device-1");

        mockMvc.perform(post("/api/1/meetings/{id}/join-requests:accept", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\": [\"" + requestId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.results[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.results[0].token").isNotEmpty())
                .andExpect(jsonPath("$.results[0].roomName").value("meeting-" + meetingId));
    }

    @Test
    void declinePendingRequestReturns200Denied() throws Exception {
        UUID meetingId = insertMeeting(50);
        UUID requestId = createPendingRequest(meetingId, "device-1");

        mockMvc.perform(post("/api/1/meetings/{id}/join-requests:decline", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\": [\"" + requestId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("DENIED"))
                .andExpect(jsonPath("$.results[0].token").doesNotExist());
    }

    @Test
    void emptyBodyReturns400ValidationError() throws Exception {
        UUID meetingId = insertMeeting(50);

        mockMvc.perform(post("/api/1/meetings/{id}/join-requests:accept", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonHostCallerReturns403NotOwner() throws Exception {
        UUID meetingId = insertMeeting(50);
        UUID requestId = createPendingRequest(meetingId, "device-1");

        mockMvc.perform(post("/api/1/meetings/{id}/join-requests:accept", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "not-the-host")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\": [\"" + requestId + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
    }

    @Test
    void unknownMeetingReturns404() throws Exception {
        UUID unknownMeeting = UUID.randomUUID();

        mockMvc.perform(post("/api/1/meetings/{id}/join-requests:accept", unknownMeeting)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestIds\": [\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    private UUID createPendingRequest(UUID meetingId, String deviceId) throws Exception {
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
        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
        return UUID.fromString(requestId);
    }

    private UUID insertMeeting(int maxParticipants) {
        UUID id = UUID.randomUUID();
        String settings = """
                {"admissionPolicy": "MANUAL_APPROVAL", "maxParticipants": %d, \
                "allowScreenShare": true, "chatEnabled": true, "allowMicrophone": true, \
                "allowVideo": true}
                """.formatted(maxParticipants);
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
