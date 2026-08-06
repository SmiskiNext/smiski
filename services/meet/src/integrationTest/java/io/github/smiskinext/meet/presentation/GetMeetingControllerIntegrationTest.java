package io.github.smiskinext.meet.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class GetMeetingControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-get-detail-test";
    private static final String HOST_ID = "get-detail-host";
    private static final String MEMBER_ID = "get-detail-member";

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
    void nonHostTenantMemberGetsMeetingWithInviteesAndParticipants() throws Exception {
        UUID meetingId = createScheduledMeeting();
        insertInvitee(meetingId, "alice", "alice@example.com", "Alice", "ACCEPTED");
        Instant joinedAt = Instant.parse("2025-02-01T14:01:00Z");
        insertParticipationSession(meetingId, "alice", "PARTICIPANT", joinedAt, null);

        mockMvc.perform(get("/api/1/meetings/{id}", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", MEMBER_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.meeting.id").value(meetingId.toString()))
                .andExpect(jsonPath("$.meeting.hostId").value(HOST_ID))
                .andExpect(jsonPath("$.meeting.tenantId").doesNotExist())
                .andExpect(jsonPath("$.invitees.length()").value(1))
                .andExpect(jsonPath("$.invitees[0].id").isNotEmpty())
                .andExpect(jsonPath("$.invitees[0].accountId").value("alice"))
                .andExpect(jsonPath("$.invitees[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].accountId").value("alice"))
                .andExpect(jsonPath("$.participants[0].leftAt").doesNotExist());
    }

    @Test
    void missingAccountHeaderReturns400ProblemJson() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(get("/api/1/meetings/{id}", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void unknownMeetingReturns404MeetingNotFound() throws Exception {
        mockMvc.perform(get("/api/1/meetings/{id}", UUID.randomUUID())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", MEMBER_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void softDeletedMeetingReturns404MeetingNotFound() throws Exception {
        UUID meetingId = createScheduledMeeting();
        jdbcTemplate.update(
                "UPDATE meetings SET deleted_at = NOW(), deleted_by = ? WHERE tenant_id = ? AND id = ?",
                HOST_ID,
                TENANT_ID,
                meetingId);

        mockMvc.perform(get("/api/1/meetings/{id}", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", MEMBER_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Sprint planning","description":"Plan the next sprint",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"MANUAL_APPROVAL","maxParticipants":50,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC",
                "organizerEmail":"host@example.com","organizerDisplayName":"Host User"}
                """.formatted(start, start.plus(1, ChronoUnit.HOURS));
        MvcResult result = mockMvc.perform(post("/api/1/meetings:schedule")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id"));
    }

    private void insertInvitee(
            UUID meetingId, String accountId, String email, String displayName, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO meeting_invitees (
                    tenant_id, id, meeting_id, inviter_id, account_id, email,
                    display_name, role, rsvp, status, invited_at, responded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'REQ_PARTICIPANT', TRUE, ?, NOW(), NOW())
                """,
                TENANT_ID,
                UUID.randomUUID(),
                meetingId,
                HOST_ID,
                accountId,
                email,
                displayName,
                status);
    }

    private void insertParticipationSession(
            UUID meetingId, String accountId, String role, Instant joinedAt, Instant leftAt) {
        jdbcTemplate.update(
                """
                INSERT INTO participation_logs (
                    tenant_id, id, meeting_id, account_id, role,
                    livekit_identity, joined_at, left_at, close_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TENANT_ID,
                UUID.randomUUID(),
                meetingId,
                accountId,
                role,
                accountId + "-identity",
                java.sql.Timestamp.from(joinedAt),
                leftAt == null ? null : java.sql.Timestamp.from(leftAt),
                leftAt == null ? null : "LEFT");
    }
}
