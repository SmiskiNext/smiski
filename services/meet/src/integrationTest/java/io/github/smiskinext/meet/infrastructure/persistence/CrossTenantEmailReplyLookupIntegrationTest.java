package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves that cross-tenant lookups used by the email-reply consumer work through real Hibernate.
 *
 * <p>The consumer runs on a Kafka thread with no tenant context (defaults to "system"). These
 * tests persist data under a real tenant and then resolve it from the default/system tenant context,
 * which is the actual runtime scenario for inbound email reply processing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CrossTenantEmailReplyLookupIntegrationTest {

    private static final String REAL_TENANT = "tenant-acme";
    private static final String CALENDAR_UID = "cross-tenant-cal-uid-" + UUID.randomUUID();
    private static final String INVITEE_EMAIL = "invitee@example.com";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MeetingRepositoryAdapter meetingRepository;

    @Autowired
    private MeetingInviteeRepositoryAdapter inviteeRepository;

    @Autowired
    private MeetingInviteeJpaRepository inviteeJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID meetingId;

    @BeforeEach
    void setUp() {
        meetingId = UUID.randomUUID();
        ensureTenant(REAL_TENANT);
        insertMeeting(REAL_TENANT, meetingId, CALENDAR_UID);
        insertInvitee(REAL_TENANT, meetingId, INVITEE_EMAIL);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meeting_invitees WHERE meeting_id = ?", meetingId);
        jdbcTemplate.update("DELETE FROM meetings WHERE id = ?", meetingId);
        TenantContext.clear();
    }

    @Test
    void findByCalendarUidResolvesFromTenantlessContext() {
        TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);

        Optional<Meeting> result = meetingRepository.findByCalendarUid(CALENDAR_UID);

        assertThat(result).isPresent();
        assertThat(result.get().getId().value()).isEqualTo(meetingId);
        assertThat(result.get().getTenantId().value()).isEqualTo(REAL_TENANT);
    }

    @Test
    void findByCalendarUidReturnsEmptyForNonexistentUid() {
        TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);

        Optional<Meeting> result = meetingRepository.findByCalendarUid("no-such-uid");

        assertThat(result).isEmpty();
    }

    @Test
    void findByMeetingIdAndEmailResolvesWithCorrectTenantContext() {
        TenantContext.setCurrentTenant(REAL_TENANT);

        Optional<MeetingInvitee> result =
                inviteeRepository.findByMeetingIdAndEmail(meetingId, Email.of(INVITEE_EMAIL));

        assertThat(result).isPresent();
        assertThat(result.get().getEmail().value()).isEqualTo(INVITEE_EMAIL);
        assertThat(result.get().getTenantId().value()).isEqualTo(REAL_TENANT);
    }

    @Test
    void findByMeetingIdAndEmailFailsWithWrongTenantContext() {
        TenantContext.setCurrentTenant("wrong-tenant");

        Optional<MeetingInvitee> result =
                inviteeRepository.findByMeetingIdAndEmail(meetingId, Email.of(INVITEE_EMAIL));

        assertThat(result).isEmpty();
    }

    private void ensureTenant(String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantId);
    }

    private void insertMeeting(String tenantId, UUID id, String calendarUid) {
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, 'host', 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'ISS-1', 'PROJ', 'Cross-Tenant Meeting', 'Description', 'UTC',
                    'SCHEDULED', 'SCHEDULED', ?::jsonb)
                """,
                tenantId,
                id,
                calendarUid,
                UUID.randomUUID().toString().substring(0, 12),
                SETTINGS_JSON);
    }

    private void insertInvitee(String tenantId, UUID meetingIdValue, String email) {
        jdbcTemplate.update("""
                INSERT INTO meeting_invitees (
                    id, tenant_id, meeting_id, inviter_id, account_id, email,
                    display_name, role, rsvp, status, invited_at
                ) VALUES (?, ?, ?, 'host', 'account-1', ?, 'Invitee', 'REQ_PARTICIPANT',
                    true, 'NEEDS_ACTION', NOW())
                """, UUID.randomUUID(), tenantId, meetingIdValue, email);
    }
}
