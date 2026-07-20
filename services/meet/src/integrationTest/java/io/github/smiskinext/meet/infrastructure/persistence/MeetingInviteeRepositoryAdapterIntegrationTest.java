package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.model.InviteeRole;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.Email;
import io.github.smiskinext.meet.domain.model.valueobject.InviteeDisplayName;
import io.github.smiskinext.meet.domain.model.valueobject.InviterId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
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
class MeetingInviteeRepositoryAdapterIntegrationTest {

    private static final String TENANT_ID = TenantContext.DEFAULT_TENANT;

    @Autowired
    private MeetingInviteeRepositoryAdapter repository;

    @Autowired
    private MeetingInviteeJpaRepository jpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MeetingId meetingId;

    @BeforeEach
    void setUp() {
        meetingId = MeetingId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, TENANT_ID, TENANT_ID);
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
                TENANT_ID,
                meetingId.value(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12));
    }

    @Test
    void removedInviteeRoundTripsIsExcludedFromActiveQueriesAndEmailCanBeReinvited() {
        MeetingInvitee removedInvitee = newInvitee("invitee@example.com", "account-1");
        repository.save(removedInvitee);
        jpaRepository.flush();

        removedInvitee.remove();
        repository.save(removedInvitee);
        jpaRepository.flush();

        MeetingInvitee restored = repository.findById(removedInvitee.getId()).orElseThrow();
        assertThat(restored.getRemovedAt()).isPresent();
        assertThat(repository.findByMeetingId(meetingId.value())).isEmpty();
        assertThat(repository.findByMeetingIdAndAccountId(
                        meetingId.value(), removedInvitee.getAccountId()))
                .isEmpty();
        assertThat(repository.findPendingByAccountId(removedInvitee.getAccountId()))
                .isEmpty();
        assertThat(repository.countActiveByMeetingId(meetingId.value())).isZero();
        assertThat(repository.findSummariesByMeetingId(meetingId.value())).isEmpty();

        MeetingInvitee replacement = newInvitee("invitee@example.com", "account-2");
        repository.save(replacement);
        jpaRepository.flush();

        assertThat(repository.findByMeetingId(meetingId.value()))
                .extracting(invitee -> invitee.getId().value())
                .containsExactly(replacement.getId().value());
        assertThat(repository.countActiveByMeetingId(meetingId.value())).isEqualTo(1);
    }

    private MeetingInvitee newInvitee(String email, String accountId) {
        return MeetingInvitee.create(
                TenantId.of(TENANT_ID),
                meetingId,
                InviterId.of("host"),
                AccountId.of(accountId),
                Email.of(email),
                InviteeDisplayName.of("Invitee"),
                InviteeRole.REQ_PARTICIPANT,
                true);
    }
}
