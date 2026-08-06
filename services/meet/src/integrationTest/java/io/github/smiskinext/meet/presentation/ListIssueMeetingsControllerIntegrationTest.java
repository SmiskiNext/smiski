package io.github.smiskinext.meet.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class ListIssueMeetingsControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-issue-list";
    private static final String ISSUE_ID = "10102";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Instant base = Instant.parse("2025-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
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
    void returns200WithPageEnvelopeForPopulatedIssue() throws Exception {
        insertMeeting(ISSUE_ID, "Meeting A", base);
        insertMeeting(ISSUE_ID, "Meeting B", base.plusSeconds(10));
        insertMeeting(ISSUE_ID, "Meeting C", base.plusSeconds(20));

        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void secondPageReturnsCorrectSliceAndEchoesOffset() throws Exception {
        insertMeeting(ISSUE_ID, "A", base);
        insertMeeting(ISSUE_ID, "B", base.plusSeconds(10));
        insertMeeting(ISSUE_ID, "C", base.plusSeconds(20));
        insertMeeting(ISSUE_ID, "D", base.plusSeconds(30));
        insertMeeting(ISSUE_ID, "E", base.plusSeconds(40));

        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":2,\"pageSize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.offset").value(2))
                .andExpect(jsonPath("$.meta.pageSize").value(2))
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.meta.hasNext").value(true));
    }

    @Test
    void missingAccountHeaderReturns400ProblemJson() throws Exception {
        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("X-Account-Id header is required"));
    }

    @Test
    void pageSizeZeroReturns400() throws Exception {
        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pageSizeFiftyOneReturns400() throws Exception {
        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void negativeOffsetReturns400() throws Exception {
        mockMvc.perform(post("/api/1/issues/{issueId}/meetings", ISSUE_ID)
                        .header("X-Project-Permissions", "view-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offset\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void insertMeeting(String issueId, String title, Instant createdAt) {
        UUID id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, start_time, zone_id, type, status,
                    settings, created_at, updated_at, deleted_at
                ) VALUES (?, ?, 'host-a', 'host@example.com', 'Host User', ?, 0, ?, ?, 'SMISKI-102',
                    'SMISKI', ?, 'Description', null, 'UTC', 'SCHEDULED', 'SCHEDULED', ?::jsonb, ?, ?, null)
                """,
                TENANT_ID,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                issueId,
                title,
                SETTINGS_JSON,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
    }
}
