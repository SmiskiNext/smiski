package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class RemoveMeetingInviteesControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-remove-invitees-test";
    private static final String HOST_ID = "remove-invitees-host";

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
    void hostRemovesInviteesReturns200WithRemovedSnapshots() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitees.length()").value(1))
                .andExpect(jsonPath("$.invitees[0].id").value(aliceId.toString()))
                .andExpect(jsonPath("$.invitees[0].accountId").value("alice"))
                .andExpect(jsonPath("$.invitees[0].tenantId").doesNotExist());

        assertThat(activeInviteeCount(meetingId)).isEqualTo(1);
    }

    @Test
    void missingAccountHeaderReturns400() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(aliceId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    @Test
    void unknownMeetingReturns404() throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", UUID.randomUUID())
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void emptyInviteeIdsReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    @Test
    void unknownInviteeIdInBatchReturns404AndRemovesNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\",\"%s\"]}"
                                .formatted(aliceId, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITEE_NOT_FOUND"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    @Test
    void alreadyRemovedInviteeIdInBatchReturns404AndRemovesNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");
        UUID bobId = firstInviteeId(meetingId, "bob");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(aliceId)))
                .andExpect(status().isOk());
        assertThat(activeInviteeCount(meetingId)).isEqualTo(1);

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\",\"%s\"]}".formatted(bobId, aliceId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITEE_NOT_FOUND"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(1);
    }

    @Test
    void nonHostRemovalReturns403AndRemovesNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "not-the-host")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(aliceId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    @Test
    void nonScheduledMeetingReturns409AndRemovesNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitees(meetingId);
        UUID aliceId = firstInviteeId(meetingId, "alice");
        jdbcTemplate.update(
                "UPDATE meetings SET status = 'RUNNING' WHERE tenant_id = ? AND id = ?",
                TENANT_ID,
                meetingId);

        mockMvc.perform(post("/api/1/meetings/{id}/invitees:batchDelete", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"%s\"]}".formatted(aliceId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Invitee Remove","description":"Remove test",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"ALLOW_ALL","maxParticipants":50,
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

    private void addInvitees(UUID meetingId) throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"},
                                  {"email":"bob@test.com","accountId":"bob","displayName":"Bob"}
                                ]}
                                """))
                .andExpect(status().isOk());
    }

    private UUID firstInviteeId(UUID meetingId, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/1/meetings/{id}", meetingId)
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andReturn();
        List<String> ids = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.invitees[?(@.accountId == '" + accountId + "')].id");
        return UUID.fromString(ids.getFirst());
    }

    private long activeInviteeCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_invitees WHERE meeting_id = ? AND removed_at IS NULL",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
