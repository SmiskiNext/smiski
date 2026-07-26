package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitParticipantSid;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ParticipationLogActiveLookupIntegrationTest {

    private static final String TENANT_ID = TenantContext.DEFAULT_TENANT;
    private static final String OTHER_TENANT_ID = "other-tenant";

    @Autowired
    private ParticipationLogRepositoryAdapter repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID meetingId;

    @BeforeEach
    void setUp() {
        meetingId = UUID.randomUUID();
        insertTenant(TENANT_ID);
        insertMeeting(TENANT_ID, meetingId);
    }

    @Test
    void findActiveBySidReturnsOnlyOpenSession() {
        insertSession("account-1", "PA_open", "account-1:device-1", null);
        insertSession("account-1", "PA_closed", "account-1:device-2", Instant.now());

        assertThat(repository.findActiveBySid(LiveKitParticipantSid.of("PA_open")))
                .isPresent()
                .get()
                .extracting(log -> log.getAccountId().value())
                .isEqualTo("account-1");
        assertThat(repository.findActiveBySid(LiveKitParticipantSid.of("PA_closed")))
                .isEmpty();
    }

    @Test
    void findActiveByMeetingIdAndIdentityReturnsOnlyOpenSession() {
        insertSession("account-1", "PA_1", "account-1:device-1", null);

        assertThat(repository.findActiveByMeetingIdAndIdentity(
                        meetingId, LiveKitIdentity.of("account-1:device-1")))
                .isPresent();
        assertThat(repository.findActiveByMeetingIdAndIdentity(
                        meetingId, LiveKitIdentity.of("account-1:device-9")))
                .isEmpty();
    }

    @Test
    void findActiveByMeetingIdReturnsOnlyOpenSessions() {
        insertSession("account-1", "PA_1", "account-1:device-1", null);
        insertSession("account-2", "PA_2", "account-2:device-1", null);
        insertSession("account-3", "PA_3", "account-3:device-1", Instant.now());

        List<ParticipationLog> active = repository.findActiveByMeetingId(meetingId);

        assertThat(active)
                .extracting(log -> log.getAccountId().value())
                .containsExactlyInAnyOrder("account-1", "account-2");
    }

    @Test
    void activeLookupsAreScopedByTenant() {
        insertSession("same-tenant", "PA_same", "same-tenant:device-1", null);

        insertTenant(OTHER_TENANT_ID);
        insertMeeting(OTHER_TENANT_ID, meetingId);
        insertSessionForTenant(
                OTHER_TENANT_ID, "other-account", "PA_other", "other-account:device-1", null);

        assertThat(repository.findActiveBySid(LiveKitParticipantSid.of("PA_other")))
                .isEmpty();
        assertThat(repository.findActiveByMeetingId(meetingId))
                .extracting(log -> log.getAccountId().value())
                .containsExactly("same-tenant");
    }

    private void insertSession(String accountId, String sid, String identity, Instant leftAt) {
        insertSessionForTenant(TENANT_ID, accountId, sid, identity, leftAt);
    }

    private void insertSessionForTenant(
            String tenantId, String accountId, String sid, String identity, Instant leftAt) {
        jdbcTemplate.update(
                """
                INSERT INTO participation_logs (
                    tenant_id, id, meeting_id, account_id, role,
                    livekit_identity, livekit_participant_sid, joined_at, left_at, close_reason
                ) VALUES (?, ?, ?, ?, 'PARTICIPANT', ?, ?, NOW(), ?, ?)
                """,
                tenantId,
                UUID.randomUUID(),
                meetingId,
                accountId,
                identity,
                sid,
                leftAt == null ? null : java.sql.Timestamp.from(leftAt),
                leftAt == null ? null : "LEFT");
    }

    private void insertTenant(String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantId);
    }

    private void insertMeeting(String tenantId, UUID id) {
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, 'host', 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Title', 'Description', 'UTC',
                    'INSTANT', 'RUNNING', '{}'::jsonb)
                ON CONFLICT (tenant_id, id) DO NOTHING
                """,
                tenantId,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12));
    }
}
