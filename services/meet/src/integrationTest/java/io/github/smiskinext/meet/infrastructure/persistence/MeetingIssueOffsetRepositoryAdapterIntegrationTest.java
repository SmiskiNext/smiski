package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.port.MeetingRepository.IssueMeetingPage;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetingIssueOffsetRepositoryAdapterIntegrationTest {

    private static final String TENANT = "tenant-issue-offset";
    private static final String ISSUE_ID = "10102";
    private static final String OTHER_ISSUE_ID = "10103";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"MANUAL_APPROVAL\",\"maxParticipants\":50,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MeetingRepositoryAdapter repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Instant base = Instant.parse("2025-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        ensureTenant(TENANT);
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id = ?", TENANT);
        TenantContext.clear();
    }

    @Test
    void pagesByRowOffsetAndComputesTotalAndHasNext() {
        insertMeeting(ISSUE_ID, "A", base);
        insertMeeting(ISSUE_ID, "B", base.plusSeconds(10));
        insertMeeting(ISSUE_ID, "C", base.plusSeconds(20));
        insertMeeting(ISSUE_ID, "D", base.plusSeconds(30));
        insertMeeting(ISSUE_ID, "E", base.plusSeconds(40));

        IssueMeetingPage firstPage = repository.findSummariesByIssueId(ISSUE_ID, 0, 2);
        assertThat(firstPage.total()).isEqualTo(5);
        assertThat(firstPage.page().items()).hasSize(2);
        assertThat(firstPage.page().hasNext()).isTrue();

        IssueMeetingPage secondPage = repository.findSummariesByIssueId(ISSUE_ID, 2, 2);
        assertThat(secondPage.total()).isEqualTo(5);
        assertThat(secondPage.page().items()).hasSize(2);
        assertThat(secondPage.page().hasNext()).isTrue();

        IssueMeetingPage lastPage = repository.findSummariesByIssueId(ISSUE_ID, 4, 2);
        assertThat(lastPage.total()).isEqualTo(5);
        assertThat(lastPage.page().items()).hasSize(1);
        assertThat(lastPage.page().hasNext()).isFalse();
    }

    @Test
    void nonAlignedOffsetClearsHasNextPerSpec() {
        insertMeeting(ISSUE_ID, "A", base);
        insertMeeting(ISSUE_ID, "B", base.plusSeconds(10));
        insertMeeting(ISSUE_ID, "C", base.plusSeconds(20));
        insertMeeting(ISSUE_ID, "D", base.plusSeconds(30));
        insertMeeting(ISSUE_ID, "E", base.plusSeconds(40));

        IssueMeetingPage page = repository.findSummariesByIssueId(ISSUE_ID, 3, 2);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.page().items()).hasSize(2);
        assertThat(page.page().hasNext()).isFalse();
    }

    @Test
    void ordersCreatedAtDescThenIdDesc() {
        UUID id1 = insertMeeting(ISSUE_ID, "First", base);
        UUID id2 = insertMeeting(ISSUE_ID, "Second", base.plusSeconds(10));
        UUID id3 = insertMeeting(ISSUE_ID, "Third", base.plusSeconds(20));

        IssueMeetingPage page = repository.findSummariesByIssueId(ISSUE_ID, 0, 10);
        List<UUID> ids = page.page().items().stream().map(MeetingSummary::id).toList();

        assertThat(ids).containsExactly(id3, id2, id1);
    }

    @Test
    void filtersToGivenIssueId() {
        UUID mine = insertMeeting(ISSUE_ID, "Mine", base);
        insertMeeting(OTHER_ISSUE_ID, "Other", base.plusSeconds(10));

        IssueMeetingPage page = repository.findSummariesByIssueId(ISSUE_ID, 0, 10);
        List<UUID> ids = page.page().items().stream().map(MeetingSummary::id).toList();

        assertThat(ids).containsExactly(mine);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void excludesSoftDeletedMeetings() {
        UUID live = insertMeeting(ISSUE_ID, "Live", base);
        insertDeletedMeeting(ISSUE_ID, "Deleted", base.plusSeconds(10));

        IssueMeetingPage page = repository.findSummariesByIssueId(ISSUE_ID, 0, 10);
        List<UUID> ids = page.page().items().stream().map(MeetingSummary::id).toList();

        assertThat(ids).containsExactly(live);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void emptyIssueReturnsEmptyPageWithZeroTotal() {
        IssueMeetingPage page = repository.findSummariesByIssueId("99999", 0, 10);

        assertThat(page.page().items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page().hasNext()).isFalse();
    }

    private UUID insertMeeting(String issueId, String title, Instant createdAt) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, start_time, zone_id, type, status,
                    settings, created_at, updated_at, deleted_at
                ) VALUES (?, ?, 'host-a', 'host@example.com', 'Host User', ?, 0, ?, ?, 'SMISKI-102',
                    'SMISKI', ?, 'Description', null, 'UTC', 'SCHEDULED', 'SCHEDULED', ?::jsonb, ?, ?, null)
                """,
                TENANT,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                issueId,
                title,
                SETTINGS_JSON,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return id;
    }

    private UUID insertDeletedMeeting(String issueId, String title, Instant createdAt) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, start_time, zone_id, type, status,
                    settings, created_at, updated_at, deleted_at
                ) VALUES (?, ?, 'host-a', 'host@example.com', 'Host User', ?, 0, ?, ?, 'SMISKI-102',
                    'SMISKI', ?, 'Description', null, 'UTC', 'SCHEDULED', 'SCHEDULED', ?::jsonb, ?, ?, ?)
                """,
                TENANT,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                issueId,
                title,
                SETTINGS_JSON,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt.plus(1, ChronoUnit.HOURS)));
        return id;
    }

    private void ensureTenant(String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantId);
    }
}
