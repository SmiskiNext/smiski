package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JoinMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-test";

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
    void blankDisplayName_returns400ValidationError() throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 50);

        String requestBody = """
                {"displayName": "", "deviceId": "device-1"}
                """;

        mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "participant-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void allowAllMeeting_returns200Approved() throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 50);

        String requestBody = """
                {"displayName": "Alice", "deviceId": "device-1", \
                "avatarUrl": "https://cdn.example.com/avatar/participant-1.png"}
                """;

        mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "participant-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roomName").value("meeting-" + meetingId))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participation_logs WHERE meeting_id = ?::uuid AND left_at IS NULL",
                Long.class,
                meetingId.toString());
        assertThat(activeCount).isEqualTo(0L);
    }

    @Test
    void allowAllMeetingAtCapacity_returns409MeetingFull() throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 2);
        insertActiveSession(meetingId, "existing-1", "device-a");
        insertActiveSession(meetingId, "existing-2", "device-b");

        String requestBody = """
                {"displayName": "Alice", "deviceId": "device-1"}
                """;

        mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "participant-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("MEETING_FULL"));

        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participation_logs WHERE meeting_id = ?::uuid AND left_at IS NULL",
                Long.class,
                meetingId.toString());
        assertThat(activeCount).isEqualTo(2L);
    }

    @Test
    void manualApprovalMeeting_returns200PendingAndEnqueuesEvent() throws Exception {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL", 50);

        String requestBody = """
                {"displayName": "Alice", "deviceId": "device-1"}
                """;

        mockMvc.perform(post("/api/1/meetings/{id}:join", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "participant-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist());

        List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid",
                meetingId.toString());
        assertThat(outboxRows)
                .extracting(row -> row.get("event_type").toString())
                .contains("io.github.smiskinext.meet.join.created.v1");
    }

    private UUID insertMeeting(String admissionPolicy, int maxParticipants) {
        UUID id = UUID.randomUUID();
        String settings = """
                {"admissionPolicy": "%s", "maxParticipants": %d, "allowScreenShare": true, \
                "chatEnabled": true, "allowMicrophone": true, "allowVideo": true}
                """.formatted(admissionPolicy, maxParticipants);
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, 'host', 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Title', 'Description', 'UTC',
                    'INSTANT', 'RUNNING', ?::jsonb)
                """,
                TENANT_ID,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                settings);
        return id;
    }

    private void insertActiveSession(UUID meetingId, String accountId, String deviceId) {
        jdbcTemplate.update(
                """
                INSERT INTO participation_logs (
                    tenant_id, id, meeting_id, account_id, role,
                    livekit_identity, joined_at, left_at, close_reason
                ) VALUES (?, ?, ?, ?, 'PARTICIPANT', ?, NOW(), NULL, NULL)
                """,
                TENANT_ID,
                UUID.randomUUID(),
                meetingId,
                accountId,
                accountId + ":" + deviceId);
    }
}
