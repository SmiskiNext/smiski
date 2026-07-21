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
class UpdateMeetingInviteesControllerIntegrationTest {

    private static final String TENANT_ID = "tenant-invitees-test";
    private static final String HOST_ID = "invitees-host";

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
    void hostReplacesInviteeListReturns200WithActiveInvitees() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(put("/api/1/meetings/{id}/invitees", meetingId)
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

        mockMvc.perform(put("/api/1/meetings/{id}/invitees", meetingId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitees\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void unknownMeetingReturns404() throws Exception {
        mockMvc.perform(put("/api/1/meetings/{id}/invitees", UUID.randomUUID())
                        .header("X-Account-Id", HOST_ID)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitees\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void invalidInviteeFieldReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(put("/api/1/meetings/{id}/invitees", meetingId)
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
    void duplicateAccountIdReturns400ValidationError() throws Exception {
        UUID meetingId = createScheduledMeeting();

        mockMvc.perform(put("/api/1/meetings/{id}/invitees", meetingId)
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

    private UUID createScheduledMeeting() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"title":"Invitee Sync","description":"Sync test",
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

    private long activeInviteeCount(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_invitees WHERE meeting_id = ? AND removed_at IS NULL",
                Long.class,
                meetingId);
        return count == null ? 0 : count;
    }
}
