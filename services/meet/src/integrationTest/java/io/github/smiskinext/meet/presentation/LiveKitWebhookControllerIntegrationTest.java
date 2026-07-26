package io.github.smiskinext.meet.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LiveKitWebhookControllerIntegrationTest {

    private static final String WEBHOOK_CONTENT_TYPE = "application/webhook+json";
    private static final String TENANT_ID = "tenant-webhook";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.livekit.api-key}")
    private String apiKey;

    @Value("${app.livekit.api-secret}")
    private String apiSecret;

    private UUID meetingId;

    @BeforeEach
    void setUp() {
        meetingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);
        String settings = """
                {"admissionPolicy": "ALLOW_ALL", "maxParticipants": 50, "allowScreenShare": true, \
                "chatEnabled": true, "allowMicrophone": true, "allowVideo": true}
                """;
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, 'host', 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Title', 'Description', 'UTC',
                    'INSTANT', 'SCHEDULED', ?::jsonb)
                """,
                TENANT_ID,
                meetingId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                settings);
    }

    @Test
    void validSignedWebhookReturns200AndDrivesProcessing() throws Exception {
        String body = roomStartedBody();
        mockMvc.perform(post("/api/1/webhooks/livekit")
                        .contentType(WEBHOOK_CONTENT_TYPE)
                        .header(HttpHeaders.AUTHORIZATION, sign(body))
                        .content(body))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM meetings WHERE tenant_id = ? AND id = ?",
                    String.class,
                    TENANT_ID,
                    meetingId);
            assertThat(status).isEqualTo("RUNNING");
        });
    }

    @Test
    void invalidSignatureReturns401AndChangesNoState() throws Exception {
        String body = roomStartedBody();
        mockMvc.perform(post("/api/1/webhooks/livekit")
                        .contentType(WEBHOOK_CONTENT_TYPE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertMeetingStillScheduled();
    }

    @Test
    void missingSignatureReturns401AndChangesNoState() throws Exception {
        String body = roomStartedBody();
        mockMvc.perform(post("/api/1/webhooks/livekit")
                        .contentType(WEBHOOK_CONTENT_TYPE)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertMeetingStillScheduled();
    }

    @Test
    void malformedBodyIsRejected() throws Exception {
        String body = "{not valid webhook json";
        mockMvc.perform(post("/api/1/webhooks/livekit")
                        .contentType(WEBHOOK_CONTENT_TYPE)
                        .header(HttpHeaders.AUTHORIZATION, sign(body))
                        .content(body))
                .andExpect(status().is4xxClientError());

        assertMeetingStillScheduled();
    }

    private void assertMeetingStillScheduled() {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM meetings WHERE tenant_id = ? AND id = ?",
                String.class,
                TENANT_ID,
                meetingId);
        assertThat(status).isEqualTo("SCHEDULED");
    }

    private String roomStartedBody() {
        return """
                {"event":"room_started","id":"%s","createdAt":%d,\
                "room":{"name":"meeting-%s","metadata":"%s","sid":"RM_test"}}
                """.formatted(
                        UUID.randomUUID(), Instant.now().getEpochSecond(), meetingId, TENANT_ID);
    }

    private String sign(String body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String sha256 = Base64.getEncoder()
                .encodeToString(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        return Jwts.builder()
                .issuer(apiKey)
                .claim("sha256", sha256)
                .signWith(
                        Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }
}
