package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class DeleteMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-delete-test";
    private static final String HOST_ID = "delete-host";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

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

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id = ?", TENANT_ID);
    }

    @Test
    void hostDeleteReturnsSnapshotAndRemovesMeetingFromList() throws Exception {
        UUID meetingId = insert("SCHEDULED");

        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.deletedBy").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.deletedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT deleted_at IS NOT NULL FROM meetings WHERE id = ?",
                        Boolean.class,
                        meetingId))
                .isTrue();
        assertThat(outboxCount(meetingId)).isEqualTo(1);

        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + meetingId + "')]").isEmpty());
    }

    @Test
    void nonHostDeleteIsForbiddenAndMissingAccountIsBadRequest() throws Exception {
        UUID meetingId = insert("SCHEDULED");

        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));

        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));

        assertThat(outboxCount(meetingId)).isZero();
    }

    @Test
    void unknownAndAlreadyDeletedMeetingsReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/1/meetings/{id}", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));

        UUID meetingId = insert("SCHEDULED");
        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void runningMeetingDeletionIsConflict() throws Exception {
        UUID meetingId = insert("RUNNING");

        mockMvc.perform(delete("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_RUNNING_MEETING"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT deleted_at IS NULL FROM meetings WHERE id = ?",
                        Boolean.class,
                        meetingId))
                .isTrue();
        assertThat(outboxCount(meetingId)).isZero();
    }

    @Test
    void batchDeleteAllEligibleReturnsSnapshotsAndEmptyListIsRejected() throws Exception {
        UUID first = insert("SCHEDULED");
        UUID second = insert("COMPLETED");

        mockMvc.perform(post("/api/1/meetings:batchDelete")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingIds\":[\"" + first + "\",\"" + second + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.meetings.length()").value(2))
                .andExpect(jsonPath("$.meetings[0].id").value(first.toString()))
                .andExpect(jsonPath("$.meetings[0].deletedBy").value(HOST_ID))
                .andExpect(jsonPath("$.meetings[1].id").value(second.toString()));

        assertThat(deletedCount(first, second)).isEqualTo(2);
        assertThat(outboxCount(first)).isEqualTo(1);
        assertThat(outboxCount(second)).isEqualTo(1);

        mockMvc.perform(post("/api/1/meetings:batchDelete")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void batchDeleteWithOneIneligibleIdDeletesNothing() throws Exception {
        UUID eligible = insert("SCHEDULED");
        UUID running = insert("RUNNING");

        mockMvc.perform(post("/api/1/meetings:batchDelete")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "edit-meeting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingIds\":[\"" + eligible + "\",\"" + running + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_RUNNING_MEETING"));

        assertThat(deletedCount(eligible, running)).isZero();
        assertThat(outboxCount(eligible)).isZero();
        assertThat(outboxCount(running)).isZero();
    }

    private UUID insert(String status) {
        UUID id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status,
                    settings, created_at, updated_at
                ) VALUES (?, ?, ?, 'host@example.com', 'Host User', ?, 0, ?, 'ISS-1', 'PROJ-1',
                    'PROJ', 'Title', 'Description', 'UTC', 'SCHEDULED', ?, ?::jsonb, ?, ?)
                """,
                TENANT_ID,
                id,
                HOST_ID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                status,
                SETTINGS_JSON,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        return id;
    }

    private long deletedCount(UUID... ids) {
        long count = 0;
        for (UUID id : ids) {
            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NOT NULL FROM meetings WHERE id = ?", Boolean.class, id);
            if (Boolean.TRUE.equals(deleted)) {
                count++;
            }
        }
        return count;
    }

    private long outboxCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
