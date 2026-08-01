package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
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
class InviteeResponseControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-test";
    private static final String INVITEE_ACCOUNT = "bob-account";

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
    void accept_ownerReturns200WithAcceptedSnapshot() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", INVITEE_ACCOUNT)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seed.inviteeId()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    }

    @Test
    void tentative_ownerReturns200WithTentativeSnapshot() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:tentative",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", INVITEE_ACCOUNT)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TENTATIVE"));
    }

    @Test
    void accept_missingAccountHeaderReturns400() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest());
        assertThat(statusOf(seed.inviteeId())).isEqualTo("NEEDS_ACTION");
    }

    @Test
    void accept_nonOwnerReturns403() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "intruder-account")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden());
        assertThat(statusOf(seed.inviteeId())).isEqualTo("NEEDS_ACTION");
    }

    @Test
    void accept_unknownInviteeReturns404() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                seed.meetingId(),
                                UUID.randomUUID())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", INVITEE_ACCOUNT)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void decline_thenAcceptReturns409InvalidTransition() throws Exception {
        Seed seed = seedMeetingWithInvitee();

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:decline",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", INVITEE_ACCOUNT)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(post(
                                "/api/1/meetings/{id}/invitees/{inviteeId}:accept",
                                seed.meetingId(),
                                seed.inviteeId())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", INVITEE_ACCOUNT)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isConflict());
        assertThat(statusOf(seed.inviteeId())).isEqualTo("DECLINED");
    }

    private Seed seedMeetingWithInvitee() throws Exception {
        String requestBody = """
                {
                    "title": "Sprint Planning",
                    "description": "Daily standup for the team",
                    "issueLink": {"issueId": "10001", "issueKey": "PROJ-1", "projectKey": "PROJ"},
                    "settings": {
                        "admissionPolicy": "ALLOW_ALL",
                        "maxParticipants": 50,
                        "allowScreenShare": true,
                        "chatEnabled": true,
                        "allowMicrophone": true,
                        "allowVideo": true
                    },
                    "host": {"displayName": "Alice", "deviceId": "device-1"},
                    "organizerEmail": "alice@example.com",
                    "organizerDisplayName": "Alice",
                    "zoneId": "UTC",
                    "invitees": [
                        {"email": "bob@test.com", "accountId": "bob-account", "displayName": "Bob"}
                    ]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/1/meetings:instant")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "host-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String meetingId = JsonPath.read(result.getResponse().getContentAsString(), "$.meeting.id");
        String inviteeId = jdbcTemplate.queryForObject(
                "SELECT id FROM meeting_invitees WHERE tenant_id = ? AND meeting_id = ?::uuid AND account_id = ?",
                String.class,
                TENANT_ID,
                meetingId,
                INVITEE_ACCOUNT);
        return new Seed(meetingId, inviteeId);
    }

    private String statusOf(String inviteeId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM meeting_invitees WHERE tenant_id = ? AND id = ?::uuid",
                String.class,
                TENANT_ID,
                inviteeId);
    }

    private record Seed(String meetingId, String inviteeId) {}
}
