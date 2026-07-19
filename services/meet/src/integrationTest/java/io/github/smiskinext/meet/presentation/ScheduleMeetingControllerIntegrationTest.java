package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
class ScheduleMeetingControllerIntegrationTest {

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
    void validRequest_returns201WithTokenFreeSnapshot() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        String requestBody = """
                {
                    "title": "Sprint Planning",
                    "description": "Weekly sprint planning session",
                    "issueLink": {
                        "issueId": "10001",
                        "issueKey": "PROJ-1",
                        "projectKey": "PROJ"
                    },
                    "settings": {
                        "admissionPolicy": "ALLOW_ALL",
                        "maxParticipants": 50,
                        "allowScreenShare": true,
                        "chatEnabled": true,
                        "allowMicrophone": true,
                        "allowVideo": true
                    },
                    "timeRange": {
                        "startTime": "%s",
                        "endTime": "%s"
                    },
                    "zoneId": "Asia/Ho_Chi_Minh",
                    "invitees": [
                        {"email": "bob@test.com", "accountId": "bob-account", "displayName": "Bob"}
                    ]
                }
                """.formatted(start, end);

        MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Account-Id", "host-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.meeting.type").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meeting.hostId").value("host-account"))
                .andExpect(jsonPath("$.meeting.title").value("Sprint Planning"))
                .andExpect(
                        jsonPath("$.meeting.description").value("Weekly sprint planning session"))
                .andExpect(jsonPath("$.meeting.issueLink.issueId").value("10001"))
                .andExpect(jsonPath("$.meeting.startTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.endTime").isNotEmpty())
                .andExpect(jsonPath("$.meeting.zoneId").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.livekit").doesNotExist())
                .andExpect(jsonPath("$.meeting.tenantId").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("\"livekit\"");
        assertThat(responseBody).doesNotContain("\"tenantId\"");
    }

    @Test
    void missingAccountId_returns400AndPersistsNothing() throws Exception {
        long meetingsBefore = countTable("meetings");
        long inviteesBefore = countTable("meeting_invitees");
        long outboxBefore = countTable("outbox_event");

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        String requestBody = scheduleRequestBody(start, end);

        mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
        assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
    }

    @Nested
    class ValidationErrors {

        @Test
        void missingTimeRange_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void missingSettings_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        }
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void missingDescription_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        }
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void missingIssueLink_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        }
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void maxParticipantsExceeds100_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 101,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        }
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }
    }

    @Test
    void pastStartTime_returns400MeetingStartInPastAndPersistsNothing() throws Exception {
        long meetingsBefore = countTable("meetings");
        long inviteesBefore = countTable("meeting_invitees");
        long outboxBefore = countTable("outbox_event");

        Instant start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        String requestBody = scheduleRequestBody(start, end);

        mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Account-Id", "host-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MEETING_START_IN_PAST"));

        assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
        assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
    }

    @Nested
    class TimeZoneValidation {

        @Test
        void missingZoneId_returns400ValidationErrorAndCreatesNothing() throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        }
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void unknownIanaIdOrBareOffset_returns400ValidationErrorAndCreatesNothing()
                throws Exception {
            long meetingsBefore = countTable("meetings");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String unknownZone = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "zoneId": "Mars/Phobos"
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(unknownZone))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);

            String bareOffset = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "zoneId": "+07:00"
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bareOffset))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void validIanaZoneIsPersistedAndEchoed() throws Exception {
            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Zone Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "zoneId": "Asia/Ho_Chi_Minh"
                    }
                    """.formatted(start, end);

            MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meeting.zoneId").value("Asia/Ho_Chi_Minh"))
                    .andReturn();

            String meetingId =
                    JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");

            List<Map<String, Object>> meetings = jdbcTemplate.queryForList(
                    "SELECT zone_id FROM meetings WHERE id = ?::uuid", meetingId);
            assertThat(meetings).hasSize(1);
            assertThat(meetings.getFirst().get("zone_id").toString()).isEqualTo("Asia/Ho_Chi_Minh");
        }

        @Test
        void createdOutboxSnapshotCarriesStartTimeEndTimeAndZoneId() throws Exception {
            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Outbox Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "zoneId": "Europe/Berlin"
                    }
                    """.formatted(start, end);

            MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            String meetingId =
                    JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT event_type, payload FROM outbox_event WHERE aggregate_id = ?::uuid",
                    meetingId);

            List<String> eventTypes =
                    outboxRows.stream().map(r -> r.get("event_type").toString()).toList();
            assertThat(eventTypes).contains("io.github.smiskinext.meet.meeting.created.v1");
            assertThat(eventTypes).doesNotContain("io.github.smiskinext.meet.meeting.started.v1");

            String createdPayload = outboxRows.stream()
                    .filter(r -> r.get("event_type").toString().contains("created"))
                    .findFirst()
                    .map(r -> r.get("payload").toString())
                    .orElseThrow();
            assertThat(createdPayload).contains("Europe/Berlin");
            assertThat(createdPayload).contains(start.toString());
            assertThat(createdPayload).contains(end.toString());
        }

        @Test
        void invitationsSentOutboxCarriesZoneIdStartTimeEndTimeAndTokens() throws Exception {
            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Inv Outbox Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "zoneId": "America/New_York",
                        "invitees": [
                            {"email": "test@example.com", "accountId": "test-acc", "displayName": "Test"}
                        ]
                    }
                    """.formatted(start, end);

            MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            String meetingId =
                    JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT event_type, payload FROM outbox_event WHERE aggregate_id = ?::uuid",
                    meetingId);

            String invitationsPayload = outboxRows.stream()
                    .filter(r -> r.get("event_type").toString().contains("invitations.created"))
                    .findFirst()
                    .map(r -> r.get("payload").toString())
                    .orElseThrow();
            assertThat(invitationsPayload).contains("America/New_York");
            assertThat(invitationsPayload).contains(start.toString());
            assertThat(invitationsPayload).contains(end.toString());
            assertThat(invitationsPayload).contains("test-acc");
        }
    }

    @Nested
    class InviteeValidationErrors {

        @Test
        void invalidInviteeEmail_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "invitees": [
                            {"email": "", "accountId": "bob-account", "displayName": "Bob"}
                        ]
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void missingInviteeAccountIdOrDisplayName_returns400AndPersistsNothing() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

            String missingAccountId = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "invitees": [
                            {"email": "bob@test.com", "displayName": "Bob"}
                        ]
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(missingAccountId))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);

            String missingDisplayName = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 50,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "timeRange": {
                            "startTime": "%s",
                            "endTime": "%s"
                        },
                        "invitees": [
                            {"email": "bob@test.com", "accountId": "bob-account"}
                        ]
                    }
                    """.formatted(start, end);

            mockMvc.perform(post("/api/1/meetings:schedule")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(missingDisplayName))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }
    }

    private String scheduleRequestBody(Instant start, Instant end) {
        return """
                {
                    "title": "Test",
                    "description": "Desc",
                    "issueLink": {
                        "issueId": "10001",
                        "issueKey": "PROJ-1",
                        "projectKey": "PROJ"
                    },
                    "settings": {
                        "admissionPolicy": "ALLOW_ALL",
                        "maxParticipants": 50,
                        "allowScreenShare": true,
                        "chatEnabled": true,
                        "allowMicrophone": true,
                        "allowVideo": true
                    },
                    "timeRange": {
                        "startTime": "%s",
                        "endTime": "%s"
                    },
                    "zoneId": "UTC"
                }
                """.formatted(start, end);
    }

    private long countTable(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0;
    }
}
