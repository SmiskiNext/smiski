package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
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
class MeetingControllerIntegrationTest {

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
    void createInstant_validRequest_returns201WithSnapshotAndHostToken() throws Exception {
        String requestBody = """
                {
                    "title": "Sprint Planning",
                    "description": "Daily standup for the team",
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
                    "host": {
                        "displayName": "Alice",
                        "deviceId": "device-1",
                        "avatarUrl": "https://cdn.example.com/alice.png"
                    },
                    "organizerEmail": "alice@example.com",
                    "organizerDisplayName": "Alice",
                    "zoneId": "Asia/Ho_Chi_Minh",
                    "invitees": [
                        {"email": "bob@test.com", "accountId": "bob-account", "displayName": "Bob"}
                    ]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
                        .header("X-Account-Id", "host-account")
                        .header("X-Tenant-ID", "tenant-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.meeting.type").value("INSTANT"))
                .andExpect(jsonPath("$.meeting.status").value("RUNNING"))
                .andExpect(jsonPath("$.meeting.hostId").value("host-account"))
                .andExpect(jsonPath("$.meeting.title").value("Sprint Planning"))
                .andExpect(jsonPath("$.meeting.description").value("Daily standup for the team"))
                .andExpect(jsonPath("$.meeting.organizerEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.meeting.organizerDisplayName").value("Alice"))
                .andExpect(jsonPath("$.meeting.issueLink.issueId").value("10001"))
                .andExpect(jsonPath("$.meeting.issueLink.issueKey").value("PROJ-1"))
                .andExpect(jsonPath("$.meeting.issueLink.projectKey").value("PROJ"))
                .andExpect(jsonPath("$.livekit.token").isNotEmpty())
                .andExpect(jsonPath("$.livekit.roomName").isNotEmpty())
                .andExpect(jsonPath("$.meeting.zoneId").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.meeting.tenantId").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("meeting-");
        assertThat(responseBody).doesNotContain("\"tenantId\"");
    }

    @Nested
    class ValidationErrorsPersistNothing {

        @Test
        void missingAccountId_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "zoneId": "UTC"
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void missingSettings_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

            String requestBody = """
                    {
                        "title": "Test",
                        "description": "Desc",
                        "issueLink": {
                            "issueId": "10001",
                            "issueKey": "PROJ-1",
                            "projectKey": "PROJ"
                        },
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void missingHostFields_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

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

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void invalidInviteeEmail_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "invitees": [
                            {"email": "", "accountId": "bob-account", "displayName": "Bob"}
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void missingInviteeAccountIdOrDisplayName_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");
            long inviteesBefore = countTable("meeting_invitees");
            long outboxBefore = countTable("outbox_event");

            String requestBodyMissingAccountId = """
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "invitees": [
                            {"email": "bob@test.com", "displayName": "Bob"}
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBodyMissingAccountId))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);

            String requestBodyMissingDisplayName = """
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "invitees": [
                            {"email": "bob@test.com", "accountId": "bob-account"}
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBodyMissingDisplayName))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
            assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
            assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
        }

        @Test
        void missingDescription_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");

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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void missingIssueLink_returns400AndNothingPersisted() throws Exception {
            long meetingsBefore = countTable("meetings");

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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }

        @Test
        void maxParticipantsExceeds100_returns400AndNothingPersisted() throws Exception {
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
                            "maxParticipants": 101,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "host-account")
                            .header("X-Tenant-ID", "tenant-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

            assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        }
    }

    @Nested
    class InstantTimeZoneValidation {

        @Test
        void missingZoneId_returns400ValidationErrorAndCreatesNothing() throws Exception {
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
                        },
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        }
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "zoneId": "Mars/Phobos"
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "zoneId": "+07:00"
                    }
                    """;

            mockMvc.perform(post("/api/1/meetings:instant")
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "organizerEmail": "alice@example.com",
                        "organizerDisplayName": "Alice",
                        "zoneId": "Asia/Ho_Chi_Minh"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
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
        void createdAndStartedOutboxSnapshotsBothCarryZoneId() throws Exception {
            String requestBody = """
                    {
                        "title": "Outbox Zone Test",
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "organizerEmail": "alice@example.com",
                        "organizerDisplayName": "Alice",
                        "zoneId": "Europe/London"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
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
            assertThat(eventTypes)
                    .contains(
                            "io.github.smiskinext.meet.meeting.created.v1",
                            "io.github.smiskinext.meet.meeting.started.v1");

            for (Map<String, Object> row : outboxRows) {
                String eventType = row.get("event_type").toString();
                if (eventType.contains("created") || eventType.contains("started")) {
                    assertThat(row.get("payload").toString()).contains("Europe/London");
                }
            }
        }

        @Test
        void invitationsSentOutboxCarriesZoneIdWithAbsentStartEndTime() throws Exception {
            String requestBody = """
                    {
                        "title": "Inv Zone Test",
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
                        "host": {
                            "displayName": "Alice",
                            "deviceId": "device-1"
                        },
                        "organizerEmail": "alice@example.com",
                        "organizerDisplayName": "Alice",
                        "zoneId": "America/Chicago",
                        "invitees": [
                            {"email": "test@example.com", "accountId": "test-acc", "displayName": "Test"}
                        ]
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
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
            assertThat(invitationsPayload).contains("America/Chicago");
            assertThat(invitationsPayload).contains("test-acc");
        }
    }

    @Nested
    class LifecyclePersistence {

        @Test
        void successfulCreation_persistsMeetingAsInstantLiveWithoutHostLog() throws Exception {
            String requestBody = """
                    {
                        "title": "Lifecycle Test",
                        "description": "Lifecycle test desc",
                        "issueLink": {
                            "issueId": "10002",
                            "issueKey": "PROJ-2",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 10,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "host": {
                            "displayName": "HostUser",
                            "deviceId": "dev-42"
                        },
                        "organizerEmail": "host@example.com",
                        "organizerDisplayName": "Host User",
                        "zoneId": "UTC"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "lifecycle-host")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            String meetingId =
                    JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");

            List<Map<String, Object>> meetings = jdbcTemplate.queryForList(
                    "SELECT type, status FROM meetings WHERE id = ?::uuid", meetingId);
            assertThat(meetings).hasSize(1);
            assertThat(meetings.getFirst().get("type").toString()).isEqualTo("INSTANT");
            assertThat(meetings.getFirst().get("status").toString()).isEqualTo("RUNNING");

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM meeting_invitees WHERE meeting_id = ?::uuid",
                            Long.class,
                            meetingId))
                    .isZero();

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid", meetingId);
            List<String> eventTypes =
                    outboxRows.stream().map(r -> r.get("event_type").toString()).toList();
            assertThat(eventTypes)
                    .contains(
                            "io.github.smiskinext.meet.meeting.created.v1",
                            "io.github.smiskinext.meet.meeting.started.v1");
            assertThat(eventTypes)
                    .doesNotContain("io.github.smiskinext.meet.meeting.invitations.created.v1");
        }

        @Test
        void successfulCreationWithInvitees_persistsInviteesAndInvitationsEvent() throws Exception {
            String requestBody = """
                    {
                        "title": "Invitee Test",
                        "description": "Invitee test desc",
                        "issueLink": {
                            "issueId": "10003",
                            "issueKey": "PROJ-3",
                            "projectKey": "PROJ"
                        },
                        "settings": {
                            "admissionPolicy": "ALLOW_ALL",
                            "maxParticipants": 20,
                            "allowScreenShare": true,
                            "chatEnabled": true,
                            "allowMicrophone": true,
                            "allowVideo": true
                        },
                        "host": {
                            "displayName": "InvHost",
                            "deviceId": "dev-inv"
                        },
                        "organizerEmail": "inv-host@example.com",
                        "organizerDisplayName": "Invite Host",
                        "zoneId": "UTC",
                        "invitees": [
                            {"email": "alice@example.com", "accountId": "alice-acc", "displayName": "Alice"},
                            {"email": "carol@example.com", "accountId": "carol-acc", "displayName": "Carol"}
                        ]
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
                            .header("X-Account-Id", "invitee-host")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            String meetingId =
                    JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");

            List<Map<String, Object>> invitees = jdbcTemplate.queryForList(
                    "SELECT status, token_hash FROM meeting_invitees WHERE meeting_id = ?::uuid",
                    meetingId);
            assertThat(invitees).hasSize(2);
            for (Map<String, Object> invitee : invitees) {
                assertThat(invitee.get("status").toString()).isEqualTo("PENDING");
                assertThat(invitee.get("token_hash")).isNotNull();
            }

            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'meeting_invitees'",
                    String.class);
            assertThat(columns).doesNotContain("raw_token");

            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                    "SELECT event_type FROM outbox_event WHERE aggregate_id = ?::uuid", meetingId);
            List<String> eventTypes =
                    outboxRows.stream().map(r -> r.get("event_type").toString()).toList();
            assertThat(eventTypes)
                    .contains(
                            "io.github.smiskinext.meet.meeting.created.v1",
                            "io.github.smiskinext.meet.meeting.started.v1",
                            "io.github.smiskinext.meet.meeting.invitations.created.v1");
        }
    }

    private long countTable(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0;
    }
}
