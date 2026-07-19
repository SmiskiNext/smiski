package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class UpdateMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-update-test";
    private static final String HOST_ID = "update-host";

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
    void hostUpdateReturnsAndPersistsFullSnapshotWithBothEvents() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("ALLOW_ALL", 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.meeting.type").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.title").value("Updated title"))
                .andExpect(jsonPath("$.meeting.description").value("Updated description"))
                .andExpect(jsonPath("$.meeting.issueLink.issueKey").value("PROJ-2"))
                .andExpect(jsonPath("$.meeting.settings.maxParticipants").value(25))
                .andExpect(jsonPath("$.meeting.zoneId").value("UTC"))
                .andExpect(jsonPath("$.meeting.startTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.endTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.meeting.tenantId").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT title FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("Updated title");
        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore + 2);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid",
                        String.class,
                        meetingId))
                .contains("meeting.info.update", "meeting.settings.update");
    }

    @Test
    void authorizationIdentityAndMissingMeetingUseProblemDetails() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("ALLOW_ALL", 25)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
        mockMvc.perform(put("/api/1/meetings/{id}", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("ALLOW_ALL", 25)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("ALLOW_ALL", 25)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void invalidValuesReturnValidationErrorWithoutChanges() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        for (String body : new String[] {
            updateBody("INVALID", 25),
            updateBody("ALLOW_ALL", 101),
            updateBody("ALLOW_ALL", 25).replace("\"UTC\"", "\"Mars/Phobos\""),
            updateBody("ALLOW_ALL", 25, start.plus(1, ChronoUnit.HOURS), start)
        }) {
            mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                            .header("X-Account-Id", HOST_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT title FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("Original title");
        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore);
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Original title","description":"Original description",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"MANUAL_APPROVAL","maxParticipants":100,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"Asia/Ho_Chi_Minh"}
                """.formatted(start, start.plus(1, ChronoUnit.HOURS));
        MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id"));
    }

    private String updateBody(String policy, int maxParticipants) {
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        return updateBody(policy, maxParticipants, start, start.plus(1, ChronoUnit.HOURS));
    }

    private String updateBody(String policy, int maxParticipants, Instant start, Instant end) {
        return """
                {"title":"Updated title","description":"Updated description",
                "issueLink":{"issueId":"10002","issueKey":"PROJ-2","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"%s","maxParticipants":%d,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC"}
                """.formatted(policy, maxParticipants, start, end);
    }

    private long outboxCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
