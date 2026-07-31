package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that a LiveKit token-generation failure prevents any persistence.
 *
 * <p>With the token-first approach, the LiveKit token is generated before any persistence call.
 * When it fails, the service returns early and never persists meeting, invitees, or outbox rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeetingCreationRollbackIntegrationTest {

    private static final String TENANT_ID = "tenant-rollback";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LiveKitPort liveKitPort;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);
    }

    @Test
    void liveKitFailure_returnsServiceUnavailableAndNothingPersisted() throws Exception {
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.failure(
                        new MeetingError.LiveKitUnavailable("simulated connection refused")));

        long meetingsBefore = countTable("meetings");
        long inviteesBefore = countTable("meeting_invitees");
        long outboxBefore = countTable("outbox_event");

        String requestBody = """
                {
                    "title": "Rollback Test",
                    "description": "Rollback test desc",
                    "issueLink": {
                        "issueId": "10001",
                        "issueKey": "PROJ-1",
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
                        "displayName": "RollbackHost",
                        "deviceId": "dev-rb"
                    },
                    "organizerEmail": "rollback@example.com",
                    "organizerDisplayName": "Rollback Host",
                    "zoneId": "UTC",
                    "invitees": [
                        {"email": "inv@example.com", "accountId": "inv-acc", "displayName": "Invitee"}
                    ]
                }
                """;

        mockMvc.perform(post("/api/1/meetings:instant")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "rollback-host-account")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("LIVEKIT_UNAVAILABLE"));

        assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        assertThat(countTable("meeting_invitees")).isEqualTo(inviteesBefore);
        assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
    }

    @Test
    void liveKitFailureWithoutInvitees_returnsServiceUnavailableAndNothingPersisted()
            throws Exception {
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("timeout")));

        long meetingsBefore = countTable("meetings");
        long outboxBefore = countTable("outbox_event");

        String requestBody = """
                {
                    "title": "No Inv Test",
                    "description": "No inv test desc",
                    "issueLink": {
                        "issueId": "10001",
                        "issueKey": "PROJ-1",
                        "projectKey": "PROJ"
                    },
                    "settings": {
                        "admissionPolicy": "ALLOW_ALL",
                        "maxParticipants": 5,
                        "allowScreenShare": false,
                        "chatEnabled": true,
                        "allowMicrophone": true,
                        "allowVideo": false
                    },
                    "host": {
                        "displayName": "NoInvHost",
                        "deviceId": "dev-no-inv"
                    },
                    "organizerEmail": "no-inv@example.com",
                    "organizerDisplayName": "No Invite Host",
                    "zoneId": "UTC"
                }
                """;

        mockMvc.perform(post("/api/1/meetings:instant")
                        .header("X-Project-Permissions", "view-meeting,edit-meeting")
                        .header("X-Account-Id", "no-inv-host")
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(countTable("meetings")).isEqualTo(meetingsBefore);
        assertThat(countTable("outbox_event")).isEqualTo(outboxBefore);
    }

    private long countTable(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0;
    }
}
