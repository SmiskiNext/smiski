package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Verifies the non-locking {@code findActiveById} read used by the optimistic pre-check phase of
 * the join flows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetingActiveLookupIntegrationTest {

    private static final String TENANT = "tenant-active-lookup";
    private static final String OTHER_TENANT = "tenant-active-lookup-other";
    private static final String SETTINGS_JSON =
            "{\"admissionPolicy\":\"ALLOW_ALL\",\"maxParticipants\":42,"
                    + "\"allowScreenShare\":true,\"chatEnabled\":true,"
                    + "\"allowMicrophone\":true,\"allowVideo\":true}";

    @Autowired
    private MeetingRepositoryAdapter repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureTenant(TENANT);
        ensureTenant(OTHER_TENANT);
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meetings WHERE tenant_id in (?, ?)", TENANT, OTHER_TENANT);
        TenantContext.clear();
    }

    @Test
    void returnsActiveMeetingWithoutAcquiringLock() {
        UUID meetingId = insertMeeting(TENANT, null);

        Optional<Meeting> found = repository.findActiveById(meetingId);

        assertThat(found).isPresent();
        assertThat(found.get().getId().value()).isEqualTo(meetingId);
        assertThat(found.get().getSettings().admissionPolicy())
                .isEqualTo(AdmissionPolicy.ALLOW_ALL);
        assertThat(found.get().getSettings().maxParticipants()).isEqualTo(42);
    }

    @Test
    void excludesSoftDeletedMeeting() {
        UUID deletedId = insertMeeting(TENANT, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(repository.findActiveById(deletedId)).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownMeeting() {
        assertThat(repository.findActiveById(UuidCreator.getTimeOrderedEpoch())).isEmpty();
    }

    @Test
    void isScopedByTenant() {
        UUID otherTenantMeeting = insertMeeting(OTHER_TENANT, null);

        assertThat(repository.findActiveById(otherTenantMeeting)).isEmpty();

        TenantContext.setCurrentTenant(OTHER_TENANT);
        assertThat(repository.findActiveById(otherTenantMeeting)).isPresent();
    }

    private UUID insertMeeting(String tenantId, Instant deletedAt) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status,
                    settings, deleted_at
                ) VALUES (?, ?, 'host-a', 'host@example.com', 'Host User', ?, 0, ?, '10101',
                    'SMISKI-101', 'SMISKI', 'Title', 'Description', 'UTC', 'INSTANT', 'RUNNING',
                    ?::jsonb, ?)
                """,
                tenantId,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                SETTINGS_JSON,
                deletedAt == null ? null : java.sql.Timestamp.from(deletedAt));
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
