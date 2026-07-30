package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
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
class CancelMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-cancel-test";
    private static final String HOST_ID = "cancel-host";
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
    void hostCancelReturnsSnapshotAndPublishesEvent() throws Exception {
        UUID meetingId = insert("SCHEDULED");

        mockMvc.perform(post("/api/1/meetings/{id}:cancel", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.status").value("CANCELED"))
                .andExpect(jsonPath("$.meeting.cancelReason").value("HOST_CANCELED"))
                .andExpect(jsonPath("$.meeting.canceledAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cancel_reason FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("HOST_CANCELED");
        assertThat(outboxCount(meetingId)).isEqualTo(1);
    }

    @Test
    void nonHostCancelIsForbiddenAndMissingAccountIsBadRequest() throws Exception {
        UUID meetingId = insert("SCHEDULED");

        mockMvc.perform(post("/api/1/meetings/{id}:cancel", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));

        mockMvc.perform(post("/api/1/meetings/{id}:cancel", meetingId)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(outboxCount(meetingId)).isZero();
    }

    @Test
    void unknownMeetingReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}:cancel", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void runningMeetingCancelIsConflict() throws Exception {
        UUID meetingId = insert("RUNNING");

        mockMvc.perform(post("/api/1/meetings/{id}:cancel", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("RUNNING");
        assertThat(outboxCount(meetingId)).isZero();
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

    private long outboxCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
