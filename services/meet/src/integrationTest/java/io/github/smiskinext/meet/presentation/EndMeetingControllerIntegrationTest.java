package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.shared.domain.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EndMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-end-test";
    private static final String HOST_ID = "end-host";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LiveKitPort liveKitPort;

    @BeforeEach
    void setUp() {
        when(liveKitPort.deleteRoom(any())).thenReturn(Result.success());
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM participation_logs WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM meeting_invitees WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id = ?", TENANT_ID);
    }

    @Test
    void hostEndReturnsCompletedSnapshotAndPersistsState() throws Exception {
        UUID meetingId = insert("RUNNING");

        mockMvc.perform(post("/api/1/meetings/{id}:end", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.status").value("COMPLETED"));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM meetings WHERE id = ?", String.class, meetingId);
        assertThat(status).isEqualTo("COMPLETED");

        assertThat(outboxCount(meetingId)).isEqualTo(1);
    }

    @Test
    void nonHostEndIsForbiddenAndMeetingRemainsUnchanged() throws Exception {
        UUID meetingId = insert("RUNNING");

        mockMvc.perform(post("/api/1/meetings/{id}:end", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));

        assertThat(outboxCount(meetingId)).isZero();
    }

    @Test
    void missingAccountHeaderReturnsBadRequest() throws Exception {
        UUID meetingId = insert("RUNNING");

        mockMvc.perform(post("/api/1/meetings/{id}:end", meetingId)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(outboxCount(meetingId)).isZero();
    }

    @Test
    void unknownMeetingReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}:end", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void endNonRunningMeetingReturnsConflict() throws Exception {
        UUID meetingId = insert("SCHEDULED");

        mockMvc.perform(post("/api/1/meetings/{id}:end", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(outboxCount(meetingId)).isZero();
    }

    private UUID insert(String status) {
        UUID id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now();
        Instant start = now.minus(30, ChronoUnit.MINUTES);
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status,
                    settings, start_time, end_time, created_at, updated_at
                ) VALUES (?, ?, ?, 'host@example.com', 'Host User', ?, 0, ?, 'ISS-1', 'PROJ-1',
                    'PROJ', 'Title', 'Description', 'UTC', 'SCHEDULED', ?, ?::jsonb, ?, ?, ?, ?)
                """,
                TENANT_ID,
                id,
                HOST_ID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                status,
                SETTINGS_JSON,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(start.plus(1, ChronoUnit.HOURS)),
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
