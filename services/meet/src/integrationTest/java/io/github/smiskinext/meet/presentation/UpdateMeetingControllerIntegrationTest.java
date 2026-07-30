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
    void hostInfoUpdateReturnsSnapshotWithoutSettingsAndPublishesInfoEvent() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoUpdateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.meeting.type").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.title").value("Updated title"))
                .andExpect(jsonPath("$.meeting.description").value("Updated description"))
                .andExpect(jsonPath("$.meeting.issueLink.issueKey").value("PROJ-2"))
                .andExpect(jsonPath("$.meeting.settings").doesNotExist())
                .andExpect(jsonPath("$.meeting.zoneId").value("UTC"))
                .andExpect(jsonPath("$.meeting.startTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.endTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.organizerEmail").value("host@example.com"))
                .andExpect(jsonPath("$.meeting.organizerDisplayName").value("Host User"))
                .andExpect(jsonPath("$.meeting.calendarUid").isNotEmpty())
                .andExpect(jsonPath("$.meeting.calendarSequence").value(1))
                .andExpect(jsonPath("$.meeting.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.meeting.tenantId").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT title FROM meetings WHERE id = ?", String.class, meetingId))
                .isEqualTo("Updated title");
        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore + 1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid",
                        String.class,
                        meetingId))
                .contains("io.github.smiskinext.meet.meeting.info.updated.v1")
                .doesNotContain("meeting.settings.update");
    }

    @Test
    void authorizationIdentityAndMissingMeetingUseProblemDetails() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoUpdateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
        mockMvc.perform(put("/api/1/meetings/{id}", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoUpdateBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoUpdateBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void invalidInfoValuesReturnValidationErrorWithoutChanges() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        for (String body : new String[] {
            infoUpdateBody().replace("\"UTC\"", "\"Mars/Phobos\""),
            infoUpdateBody(start.plus(1, ChronoUnit.HOURS), start),
            infoUpdateBody().replace("\"Updated title\"", "\"\"")
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

    @Test
    void noOpInfoUpdatePublishesNoEvent() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsAfterCreation = outboxCount(meetingId);

        String startTime = jdbcTemplate.queryForObject(
                "SELECT TO_CHAR(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM meetings WHERE id = ?",
                String.class,
                meetingId);
        String endTime = jdbcTemplate.queryForObject(
                "SELECT TO_CHAR(end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM meetings WHERE id = ?",
                String.class,
                meetingId);
        String noOpBody = """
                {"title":"Original title","description":"Original description",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"Asia/Ho_Chi_Minh"}
                """.formatted(startTime, endTime);

        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noOpBody))
                .andExpect(status().isOk());

        assertThat(outboxCount(meetingId)).isEqualTo(eventsAfterCreation);
    }

    @Test
    void hostReplacesSettingsReturns200WithSettingsSnapshot() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);

        mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody("ALLOW_ALL", 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.admissionPolicy").value("ALLOW_ALL"))
                .andExpect(jsonPath("$.maxParticipants").value(25))
                .andExpect(jsonPath("$.allowScreenShare").value(true))
                .andExpect(jsonPath("$.chatEnabled").value(true))
                .andExpect(jsonPath("$.allowMicrophone").value(true))
                .andExpect(jsonPath("$.allowVideo").value(true));

        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore + 1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid",
                        String.class,
                        meetingId))
                .contains("meeting.settings.update");
    }

    @Test
    void settingsNonHostReturns403() throws Exception {
        UUID meetingId = createScheduledMeeting();
        mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                        .header("X-Account-Id", "other-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody("ALLOW_ALL", 25)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
    }

    @Test
    void settingsMissingAccountReturns400() throws Exception {
        UUID meetingId = createScheduledMeeting();
        mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody("ALLOW_ALL", 25)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void settingsUnknownMeetingReturns404() throws Exception {
        mockMvc.perform(put("/api/1/meetings/{id}/settings", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody("ALLOW_ALL", 25)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void settingsInvalidValuesReturnValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);
        for (String body : new String[] {
            settingsBody("INVALID", 25),
            settingsBody("ALLOW_ALL", 101),
            settingsBody("ALLOW_ALL", 1)
        }) {
            mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                            .header("X-Account-Id", HOST_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore);
    }

    @Test
    void settingsNoOpPublishesNoEvent() throws Exception {
        UUID meetingId = createScheduledMeeting();
        long eventsBefore = outboxCount(meetingId);

        mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsBody("MANUAL_APPROVAL", 100)))
                .andExpect(status().isOk());

        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore);
    }

    @Test
    void runningMeetingAcceptsInfoButRejectsScheduledFieldChange() throws Exception {
        UUID meetingId = insertMeeting("RUNNING");
        long eventsBefore = outboxCount(meetingId);

        String startTime = jdbcTemplate.queryForObject(
                "SELECT TO_CHAR(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM meetings WHERE id = ?",
                String.class,
                meetingId);
        String endTime = jdbcTemplate.queryForObject(
                "SELECT TO_CHAR(end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM meetings WHERE id = ?",
                String.class,
                meetingId);

        String infoOnlyBody = """
                {"title":"Running update","description":"Updated while running",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC"}
                """.formatted(startTime, endTime);
        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoOnlyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meeting.title").value("Running update"));

        assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore + 1);

        String zoneChangeBody = """
                {"title":"Running update","description":"Updated while running",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"America/New_York"}
                """.formatted(startTime, endTime);
        mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zoneChangeBody))
                .andExpect(status().isConflict());
    }

    @Test
    void completedAndCanceledMeetingsRejectInfoUpdates() throws Exception {
        for (String meetingStatus : new String[] {"COMPLETED", "CANCELED"}) {
            UUID meetingId = insertMeeting(meetingStatus);
            mockMvc.perform(put("/api/1/meetings/{id}", meetingId)
                            .header("X-Account-Id", HOST_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(infoUpdateBody()))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void completedAndCanceledMeetingsRejectSettingsReplacement() throws Exception {
        for (String meetingStatus : new String[] {"COMPLETED", "CANCELED"}) {
            UUID meetingId = insertMeeting(meetingStatus);
            long eventsBefore = outboxCount(meetingId);
            mockMvc.perform(put("/api/1/meetings/{id}/settings", meetingId)
                            .header("X-Account-Id", HOST_ID)
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(settingsBody("ALLOW_ALL", 25)))
                    .andExpect(status().isConflict());
            assertThat(outboxCount(meetingId)).isEqualTo(eventsBefore);
        }
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Original title","description":"Original description",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"MANUAL_APPROVAL","maxParticipants":100,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"Asia/Ho_Chi_Minh",
                "organizerEmail":"host@example.com","organizerDisplayName":"Host User"}
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

    private UUID insertMeeting(String status) {
        UUID id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant start = now.minus(30, ChronoUnit.MINUTES);
        String settingsJson = "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":100,"
                + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                + "\"allowMicrophone\":true,\"allowVideo\":true}";
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
                settingsJson,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(start.plus(1, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        return id;
    }

    private String infoUpdateBody() {
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        return infoUpdateBody(start, start.plus(1, ChronoUnit.HOURS));
    }

    private String infoUpdateBody(Instant start, Instant end) {
        return """
                {"title":"Updated title","description":"Updated description",
                "issueLink":{"issueId":"10002","issueKey":"PROJ-2","projectKey":"PROJ"},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC"}
                """.formatted(start, end);
    }

    private String settingsBody(String policy, int maxParticipants) {
        return """
                {"admissionPolicy":"%s","maxParticipants":%d,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true}
                """.formatted(policy, maxParticipants);
    }

    private long outboxCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
