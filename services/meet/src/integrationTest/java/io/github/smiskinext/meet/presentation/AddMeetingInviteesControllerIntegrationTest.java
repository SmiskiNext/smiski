package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AddMeetingInviteesControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-add-invitees-test";
    private static final String HOST_ID = "add-invitees-host";

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
    void hostAddsInviteesReturns200WithCreatedSnapshots() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"},
                                  {"email":"bob@test.com","accountId":"bob","displayName":"Bob"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitees.length()").value(2))
                .andExpect(jsonPath("$.invitees[0].accountId").value("alice"))
                .andExpect(jsonPath("$.invitees[0].status").value("NEEDS_ACTION"))
                .andExpect(jsonPath("$.invitees[0].id").isNotEmpty())
                .andExpect(jsonPath("$.invitees[0].tenantId").doesNotExist());

        assertThat(activeInviteeCount(meetingId)).isEqualTo(2);
    }

    @Test
    void missingAccountHeaderReturns400() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    @Test
    void unknownMeetingReturns404() throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}/invitees", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void invalidInviteeFieldReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"not-an-email","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    @Test
    void emptyInviteesListReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitees\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    @Test
    void inRequestDuplicateAccountIdReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"a@test.com","accountId":"dup","displayName":"A"},
                                  {"email":"b@test.com","accountId":"dup","displayName":"B"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    @Test
    void alreadyActiveAccountReturns409AndPersistsNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitee(meetingId, "alice", "alice@test.com", "Alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"bob@test.com","accountId":"bob","displayName":"Bob"},
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITEE_ALREADY_EXISTS"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(1);
    }

    @Test
    void reAddingSoftDeletedAccountCreatesFreshInvitee() throws Exception {
        UUID meetingId = createScheduledMeeting();
        addInvitee(meetingId, "alice", "alice@test.com", "Alice");
        jdbcTemplate.update(
                "UPDATE meeting_invitees SET removed_at = NOW() WHERE meeting_id = ? AND account_id = ?",
                meetingId,
                "alice");

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitees.length()").value(1))
                .andExpect(jsonPath("$.invitees[0].accountId").value("alice"))
                .andExpect(jsonPath("$.invitees[0].status").value("NEEDS_ACTION"));

        assertThat(activeInviteeCount(meetingId)).isEqualTo(1);
    }

    @Test
    void nonHostAdditionReturns403AndPersistsNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", "not-the-host")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    @Test
    void nonScheduledMeetingReturns409AndPersistsNothing() throws Exception {
        UUID meetingId = createScheduledMeeting();
        jdbcTemplate.update(
                "UPDATE meetings SET status = 'RUNNING' WHERE tenant_id = ? AND id = ?",
                TENANT_ID,
                meetingId);

        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"alice@test.com","accountId":"alice","displayName":"Alice"}
                                ]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(activeInviteeCount(meetingId)).isZero();
    }

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Invitee Add","description":"Add test",
                "issueLink":{"issueId":"10001","issueKey":"PROJ-1","projectKey":"PROJ"},
                "settings":{"admissionPolicy":"ALLOW_ALL","maxParticipants":50,
                "allowScreenShare":true,"chatEnabled":true,"allowMicrophone":true,"allowVideo":true},
                "timeRange":{"startTime":"%s","endTime":"%s"},"zoneId":"UTC",
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

    private void addInvitee(UUID meetingId, String accountId, String email, String displayName)
            throws Exception {
        mockMvc.perform(post("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitees":[
                                  {"email":"%s","accountId":"%s","displayName":"%s"}
                                ]}
                                """.formatted(email, accountId, displayName)))
                .andExpect(status().isOk());
    }

    private long activeInviteeCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_invitees WHERE meeting_id = ? AND removed_at IS NULL",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
