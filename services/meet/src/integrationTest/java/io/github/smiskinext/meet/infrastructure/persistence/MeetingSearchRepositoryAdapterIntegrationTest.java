package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
class MeetingSearchRepositoryAdapterIntegrationTest {

    private static final String TENANT = "tenant-search";
    private static final String OTHER_TENANT = "tenant-other";
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
        ensureTenant(OTHER_TENANT);
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id IN (?, ?)", TENANT, OTHER_TENANT);
        TenantContext.clear();
    }

    @Test
    void listsOnlyRequestTenantMeetings() {
        UUID mine = insert(TENANT, "host-a", "Mine", "PROJ-1", "SCHEDULED", null, base, false);
        insert(OTHER_TENANT, "host-a", "Theirs", "PROJ-1", "SCHEDULED", null, base, false);

        List<UUID> ids = ids(search(
                criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, null), 20));

        assertThat(ids).containsExactly(mine);
    }

    @Test
    void filtersByCreator() {
        UUID hostA = insert(TENANT, "host-a", "A", "PROJ-1", "SCHEDULED", null, base, false);
        insert(TENANT, "host-b", "B", "PROJ-1", "SCHEDULED", null, base.plusSeconds(1), false);

        List<UUID> ids = ids(search(
                criteria(
                        AccountId.of("host-a"),
                        null,
                        Set.of(),
                        null,
                        MeetingSortField.CREATED_AT,
                        null),
                20));

        assertThat(ids).containsExactly(hostA);
    }

    @Test
    void filtersByStatus() {
        UUID scheduled = insert(TENANT, "host-a", "S", "PROJ-1", "SCHEDULED", null, base, false);
        UUID running = insert(
                TENANT, "host-a", "R", "PROJ-1", "RUNNING", null, base.plusSeconds(1), false);
        insert(TENANT, "host-a", "C", "PROJ-1", "COMPLETED", null, base.plusSeconds(2), false);

        List<UUID> ids = ids(search(
                criteria(
                        null,
                        null,
                        Set.of(MeetingStatus.SCHEDULED, MeetingStatus.RUNNING),
                        null,
                        MeetingSortField.CREATED_AT,
                        null),
                20));

        assertThat(ids).containsExactlyInAnyOrder(scheduled, running);
    }

    @Test
    void filtersByIssueKey() {
        UUID match = insert(TENANT, "host-a", "M", "SMISKI-102", "SCHEDULED", null, base, false);
        insert(TENANT, "host-a", "N", "SMISKI-999", "SCHEDULED", null, base.plusSeconds(1), false);

        List<UUID> ids = ids(search(
                criteria(null, null, Set.of(), "SMISKI-102", MeetingSortField.CREATED_AT, null),
                20));

        assertThat(ids).containsExactly(match);
    }

    @Test
    void searchMatchesTitleOrIssueKeyCaseInsensitively() {
        UUID titleMatch = insert(
                TENANT, "host-a", "Retro follow-up", "PROJ-1", "SCHEDULED", null, base, false);
        UUID issueMatch = insert(
                TENANT,
                "host-a",
                "Standup",
                "SMISKI-102",
                "SCHEDULED",
                null,
                base.plusSeconds(1),
                false);
        insert(
                TENANT,
                "host-a",
                "Planning",
                "PROJ-9",
                "SCHEDULED",
                null,
                base.plusSeconds(2),
                false);

        List<UUID> byTitle = ids(search(
                criteria(null, "RETRO", Set.of(), null, MeetingSortField.CREATED_AT, null), 20));
        assertThat(byTitle).containsExactly(titleMatch);

        List<UUID> byIssue = ids(search(
                criteria(null, "smiski-102", Set.of(), null, MeetingSortField.CREATED_AT, null),
                20));
        assertThat(byIssue).containsExactly(issueMatch);
    }

    @Test
    void combinedFiltersAreConjunctive() {
        UUID match = insert(TENANT, "host-a", "Retro", "SMISKI-102", "RUNNING", null, base, false);
        insert(
                TENANT,
                "host-b",
                "Retro",
                "SMISKI-102",
                "RUNNING",
                null,
                base.plusSeconds(1),
                false);
        insert(
                TENANT,
                "host-a",
                "Retro",
                "SMISKI-102",
                "COMPLETED",
                null,
                base.plusSeconds(2),
                false);

        List<UUID> ids = ids(search(
                criteria(
                        AccountId.of("host-a"),
                        "retro",
                        Set.of(MeetingStatus.RUNNING),
                        "SMISKI-102",
                        MeetingSortField.CREATED_AT,
                        null),
                20));

        assertThat(ids).containsExactly(match);
    }

    @Test
    void excludesSoftDeletedMeetings() {
        UUID live = insert(TENANT, "host-a", "Live", "PROJ-1", "SCHEDULED", null, base, false);
        insert(TENANT, "host-a", "Deleted", "PROJ-1", "SCHEDULED", null, base.plusSeconds(1), true);

        List<UUID> ids = ids(search(
                criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, null), 20));

        assertThat(ids).containsExactly(live);
    }

    @Test
    void ordersByCreatedAtDescending() {
        UUID oldest = insert(TENANT, "host-a", "1", "PROJ-1", "SCHEDULED", null, base, false);
        UUID middle = insert(
                TENANT, "host-a", "2", "PROJ-1", "SCHEDULED", null, base.plusSeconds(10), false);
        UUID newest = insert(
                TENANT, "host-a", "3", "PROJ-1", "SCHEDULED", null, base.plusSeconds(20), false);

        List<UUID> ids = ids(search(
                criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, null), 20));

        assertThat(ids).containsExactly(newest, middle, oldest);
    }

    @Test
    void startTimeSortUsesCreatedAtWhenStartTimeNull() {
        UUID instant = insert(
                TENANT,
                "host-a",
                "Instant",
                "PROJ-1",
                "RUNNING",
                null,
                base.plusSeconds(30),
                false);
        UUID scheduledEarly = insert(
                TENANT,
                "host-a",
                "Early",
                "PROJ-1",
                "SCHEDULED",
                base.plusSeconds(10),
                base,
                false);
        UUID scheduledLate = insert(
                TENANT,
                "host-a",
                "Late",
                "PROJ-1",
                "SCHEDULED",
                base.plusSeconds(40),
                base.plusSeconds(1),
                false);

        List<UUID> ids = ids(search(
                criteria(null, null, Set.of(), null, MeetingSortField.START_TIME, null), 20));

        assertThat(ids).containsExactly(scheduledLate, instant, scheduledEarly);
    }

    @Test
    void keysetPagingHasNoOverlap() {
        UUID a = insert(
                TENANT, "host-a", "A", "PROJ-1", "SCHEDULED", null, base.plusSeconds(30), false);
        UUID b = insert(
                TENANT, "host-a", "B", "PROJ-1", "SCHEDULED", null, base.plusSeconds(20), false);
        UUID c = insert(
                TENANT, "host-a", "C", "PROJ-1", "SCHEDULED", null, base.plusSeconds(10), false);

        CursorPageResponse<MeetingSummary> firstPage =
                search(criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, null), 2);
        assertThat(ids(firstPage)).containsExactly(a, b);
        assertThat(firstPage.hasNext()).isTrue();

        MeetingSummary last = firstPage.items().getLast();
        MeetingSearchCriteria.Position position =
                new MeetingSearchCriteria.Position(last.createdAt(), last.id());
        CursorPageResponse<MeetingSummary> secondPage = search(
                criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, position), 2);

        assertThat(ids(secondPage)).containsExactly(c);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void emptyResultWhenNoMatches() {
        insert(TENANT, "host-a", "A", "PROJ-1", "SCHEDULED", null, base, false);

        CursorPageResponse<MeetingSummary> page = search(
                criteria(null, null, Set.of(), "NOPE-1", MeetingSortField.CREATED_AT, null), 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void summaryCarriesIssueKeyAndNoTenant() {
        insert(TENANT, "host-a", "A", "SMISKI-42", "SCHEDULED", null, base, false);

        MeetingSummary summary = search(
                        criteria(null, null, Set.of(), null, MeetingSortField.CREATED_AT, null), 20)
                .items()
                .getFirst();

        assertThat(summary.issueKey()).isEqualTo("SMISKI-42");
        assertThat(summary.hostId()).isEqualTo("host-a");
    }

    private CursorPageResponse<MeetingSummary> search(
            MeetingSearchCriteria criteria, int pageSize) {
        return repository.searchSummaries(criteria, pageSize);
    }

    private static MeetingSearchCriteria criteria(
            @Nullable AccountId creatorId,
            @Nullable String search,
            Set<MeetingStatus> statuses,
            @Nullable String issueKey,
            MeetingSortField sort,
            MeetingSearchCriteria.@Nullable Position position) {
        return new MeetingSearchCriteria(creatorId, search, statuses, issueKey, sort, position);
    }

    private static List<UUID> ids(CursorPageResponse<MeetingSummary> page) {
        return page.items().stream().map(MeetingSummary::id).toList();
    }

    private void ensureTenant(String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantId);
    }

    private UUID insert(
            String tenantId,
            String hostId,
            String title,
            String issueKey,
            String status,
            @Nullable Instant startTime,
            Instant createdAt,
            boolean deleted) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, start_time, zone_id, type, status,
                    settings, created_at, updated_at, deleted_at
                ) VALUES (?, ?, ?, 'host@example.com', 'Host User', ?, 0, ?, 'ISS-1', ?,
                    'PROJ', ?, 'Description', ?, 'UTC', 'SCHEDULED', ?, ?::jsonb, ?, ?, ?)
                """,
                tenantId,
                id,
                hostId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                issueKey,
                title,
                startTime != null ? java.sql.Timestamp.from(startTime) : null,
                status,
                SETTINGS_JSON,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                deleted ? java.sql.Timestamp.from(createdAt.plus(1, ChronoUnit.HOURS)) : null);
        return id;
    }
}
