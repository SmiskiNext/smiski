package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.projection.ParticipantSummary;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ParticipationLogRepositoryAdapterIntegrationTest {

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
    void rejoiningAccountCollapsesToOneParticipantWithEarliestJoinedAt() {
        Instant firstJoin = Instant.parse("2025-02-01T14:00:00Z");
        Instant firstLeave = Instant.parse("2025-02-01T14:10:00Z");
        Instant secondJoin = Instant.parse("2025-02-01T14:20:00Z");
        Instant secondLeave = Instant.parse("2025-02-01T14:30:00Z");
        insertSession(
                TENANT_ID,
                meetingId,
                "account-1",
                "Alice v1",
                "PARTICIPANT",
                firstJoin,
                firstLeave);
        insertSession(
                TENANT_ID, meetingId, "account-1", "Alice v2", "HOST", secondJoin, secondLeave);

        List<ParticipantSummary> participants =
                repository.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants).singleElement().satisfies(participant -> {
            assertThat(participant.accountId()).isEqualTo("account-1");
            assertThat(participant.joinedAt()).isEqualTo(firstJoin);
            assertThat(participant.leftAt()).isEqualTo(secondLeave);
            assertThat(participant.displayName()).isEqualTo("Alice v2");
            assertThat(participant.role()).isEqualTo("HOST");
        });
    }

    @Test
    void leftAccountHasLatestLeftAtWhileOpenSessionYieldsNullLeftAt() {
        Instant leftJoin = Instant.parse("2025-02-01T14:00:00Z");
        Instant leftLeave = Instant.parse("2025-02-01T14:15:00Z");
        insertSession(
                TENANT_ID, meetingId, "left-account", "Bob", "PARTICIPANT", leftJoin, leftLeave);

        Instant openFirstJoin = Instant.parse("2025-02-01T14:05:00Z");
        Instant openFirstLeave = Instant.parse("2025-02-01T14:12:00Z");
        Instant openSecondJoin = Instant.parse("2025-02-01T14:20:00Z");
        insertSession(
                TENANT_ID,
                meetingId,
                "open-account",
                "Carol",
                "PARTICIPANT",
                openFirstJoin,
                openFirstLeave);
        insertSession(
                TENANT_ID, meetingId, "open-account", "Carol", "PARTICIPANT", openSecondJoin, null);

        List<ParticipantSummary> participants =
                repository.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants).hasSize(2);
        assertThat(participants)
                .filteredOn(participant -> participant.accountId().equals("left-account"))
                .singleElement()
                .satisfies(participant -> assertThat(participant.leftAt()).isEqualTo(leftLeave));
        assertThat(participants)
                .filteredOn(participant -> participant.accountId().equals("open-account"))
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.joinedAt()).isEqualTo(openFirstJoin);
                    assertThat(participant.leftAt()).isNull();
                });
    }

    @Test
    void participationLogsOfAnotherTenantAreNotReturned() {
        Instant joinedAt = Instant.parse("2025-02-01T14:00:00Z");
        insertSession(TENANT_ID, meetingId, "same-tenant", "Dan", "PARTICIPANT", joinedAt, null);

        insertTenant(OTHER_TENANT_ID);
        insertMeeting(OTHER_TENANT_ID, meetingId);
        insertSession(
                OTHER_TENANT_ID,
                meetingId,
                "other-tenant-account",
                "Eve",
                "PARTICIPANT",
                joinedAt,
                null);

        List<ParticipantSummary> participants =
                repository.findDistinctParticipantSummariesByMeetingId(meetingId);

        assertThat(participants)
                .extracting(ParticipantSummary::accountId)
                .containsExactly("same-tenant");
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
                    'SCHEDULED', 'SCHEDULED', '{}'::jsonb)
                """,
                tenantId,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12));
    }

    private void insertSession(
            String tenantId,
            UUID meeting,
            String accountId,
            String displayName,
            String role,
            Instant joinedAt,
            Instant leftAt) {
        jdbcTemplate.update(
                """
                INSERT INTO participation_logs (
                    tenant_id, id, meeting_id, account_id, display_name, role,
                    livekit_identity, joined_at, left_at, close_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                UUID.randomUUID(),
                meeting,
                accountId,
                displayName,
                role,
                accountId + "-" + joinedAt.truncatedTo(ChronoUnit.MILLIS),
                java.sql.Timestamp.from(joinedAt),
                leftAt == null ? null : java.sql.Timestamp.from(leftAt),
                leftAt == null ? null : "LEFT");
    }
}
