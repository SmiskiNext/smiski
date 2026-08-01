package io.github.smiskinext.meet.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ListMeetingsControllerIntegrationTest {

    private static final String TENANT = "tenant-list-ctrl";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Instant base = Instant.parse("2025-06-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT, TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id = ?", TENANT);
    }

    @Test
    void emptyBodyReturnsTenantMeetingsWithDefaults() throws Exception {
        UUID newer = insert("Newer", "SMISKI-2", base.plusSeconds(10));
        insert("Older", "SMISKI-1", base);

        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.data[0].issueKey").value("SMISKI-2"))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.meta.size").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.meta.nextPageToken").doesNotExist());
    }

    @Test
    void missingAccountHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void pageSizeOverMaxIsRejected() throws Exception {
        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\": 51}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void firstPageReturnsTokenAndFollowUpHasNoOverlap() throws Exception {
        UUID a = insert("A", "PROJ-1", base.plusSeconds(30));
        UUID b = insert("B", "PROJ-1", base.plusSeconds(20));
        UUID c = insert("C", "PROJ-1", base.plusSeconds(10));

        MvcResult first = mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(a.toString()))
                .andExpect(jsonPath("$.data[1].id").value(b.toString()))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.meta.nextPageToken").isNotEmpty())
                .andReturn();

        String token =
                JsonPath.read(first.getResponse().getContentAsString(), "$.meta.nextPageToken");

        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\": 2, \"pageToken\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(c.toString()))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void tokenReusedUnderDifferentSortIsRejected() throws Exception {
        insert("A", "PROJ-1", base.plusSeconds(30));
        insert("B", "PROJ-1", base.plusSeconds(20));
        insert("C", "PROJ-1", base.plusSeconds(10));

        MvcResult first = mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\": 1, \"sort\": \"CREATED_AT\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token =
                JsonPath.read(first.getResponse().getContentAsString(), "$.meta.nextPageToken");

        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageSize\": 1, \"sort\": \"START_TIME\", \"pageToken\": \""
                                + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/1/meetings")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "account-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageToken\": \"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    private UUID insert(String title, String issueKey, Instant createdAt) {
        UUID id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status,
                    settings, created_at, updated_at
                ) VALUES (?, ?, 'account-1', 'host@example.com', 'Host User', ?, 0, ?, 'ISS-1', ?,
                    'PROJ', ?, 'Description', 'UTC', 'SCHEDULED', 'SCHEDULED', ?::jsonb, ?, ?)
                """,
                TENANT,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                issueKey,
                title,
                SETTINGS_JSON,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return id;
    }
}
