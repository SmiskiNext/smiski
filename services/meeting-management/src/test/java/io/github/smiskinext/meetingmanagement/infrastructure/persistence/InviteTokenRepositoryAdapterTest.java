package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.domain.model.InviteToken;
import io.github.smiskinext.meetingmanagement.domain.model.InviteTokenStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.InviteeId;
import io.github.phunguy65.zms.shared.domain.Result;
import io.github.phunguy65.zms.shared.domain.valueobject.MeetingId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link InviteTokenRepositoryAdapter}.
 * Verifies that JPQL bulk-update queries and finder methods return correct domain objects.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class InviteTokenRepositoryAdapterTest {

    @Autowired
    InviteTokenRepositoryAdapter adapter;

    @Autowired
    MeetingJpaRepository meetingRepository;

    @Autowired
    MeetingInviteeJpaRepository inviteeRepository;

    private static final Instant FUTURE = Instant.now().plus(7, ChronoUnit.DAYS);

    @Test
    void save_andFindById_returnsPersistedToken() {
        InviteToken token = createPendingToken();
        adapter.save(token);

        Optional<InviteToken> found = adapter.findById(token.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InviteTokenStatus.PENDING);
    }

    @Test
    void findByMeetingId_returnsAllTokensForMeeting() {
        UUID meetingId = UUID.randomUUID();
        InviteToken t1 = createPendingTokenForMeeting(MeetingId.of(meetingId));
        InviteToken t2 = createPendingTokenForMeeting(MeetingId.of(meetingId));
        adapter.save(t1);
        adapter.save(t2);

        List<InviteToken> tokens = adapter.findByMeetingId(meetingId);

        assertThat(tokens).hasSize(2);
    }

    @Test
    void findByMeetingIdAndStatus_filtersByStatus() {
        UUID meetingId = UUID.randomUUID();
        InviteToken pending = createPendingTokenForMeeting(MeetingId.of(meetingId));
        InviteToken revoked = createPendingTokenForMeeting(MeetingId.of(meetingId));
        revoked.revoke();
        adapter.save(pending);
        adapter.save(revoked);

        List<InviteToken> pendingOnly =
                adapter.findByMeetingIdAndStatus(meetingId, InviteTokenStatus.PENDING);
        List<InviteToken> revokedOnly =
                adapter.findByMeetingIdAndStatus(meetingId, InviteTokenStatus.REVOKED);

        assertThat(pendingOnly).hasSize(1);
        assertThat(revokedOnly).hasSize(1);
    }

    @Test
    void revokeAllPendingByMeetingId_revokesOnlyPendingTokens() {
        UUID meetingId = UUID.randomUUID();
        InviteToken p1 = createPendingTokenForMeeting(MeetingId.of(meetingId));
        InviteToken p2 = createPendingTokenForMeeting(MeetingId.of(meetingId));
        InviteToken used = createPendingTokenForMeeting(MeetingId.of(meetingId));
        used.markUsed();
        adapter.save(p1);
        adapter.save(p2);
        adapter.save(used);

        int count = adapter.revokeAllPendingByMeetingId(meetingId);

        assertThat(count).isEqualTo(2);
        assertThat(adapter.findByMeetingIdAndStatus(meetingId, InviteTokenStatus.REVOKED))
                .hasSize(2);
        assertThat(adapter.findByMeetingIdAndStatus(meetingId, InviteTokenStatus.PENDING))
                .isEmpty();
        assertThat(adapter.findByMeetingIdAndStatus(meetingId, InviteTokenStatus.USED))
                .hasSize(1);
    }

    @Test
    void existsByTokenHash_returnsTrueForStoredHash() {
        InviteToken token = createPendingToken();
        adapter.save(token);

        assertThat(adapter.existsByTokenHash(token.getTokenHash())).isTrue();
        assertThat(adapter.existsByTokenHash("not-a-stored-hash")).isFalse();
    }

    @Test
    void findByTokenHash_returnsDomainObject() {
        InviteToken token = createPendingToken();
        adapter.save(token);

        Optional<InviteToken> found = adapter.findByTokenHash(token.getTokenHash());

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(token.getTokenHash());
    }

    private MeetingJpaEntity saveMeeting(UUID meetingId) {
        return meetingRepository.save(new MeetingJpaEntity(
                meetingId,
                UUID.randomUUID(),
                "SC-" + meetingId.toString().substring(0, 8),
                "Test meeting",
                null,
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T10:00:00Z"),
                "SCHEDULED",
                "LIVE",
                new MeetingSettingsJson("ALLOW_ALL", true, 100, true, true, true, true, null),
                Instant.parse("2026-04-01T08:00:00Z")));
    }

    private MeetingInviteeJpaEntity saveInvitee(UUID inviteeId, UUID meetingId) {
        return inviteeRepository.save(new MeetingInviteeJpaEntity(
                inviteeId,
                meetingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                inviteeId + "@example.com",
                "Test Invitee",
                "PENDING",
                Instant.parse("2026-04-01T08:00:00Z"),
                null,
                null));
    }

    private InviteToken createPendingToken() {
        return createPendingTokenForMeeting(MeetingId.of(UUID.randomUUID()));
    }

    private InviteToken createPendingTokenForMeeting(MeetingId meetingId) {
        saveMeeting(meetingId.value());
        UUID inviteeId = UUID.randomUUID();
        saveInvitee(inviteeId, meetingId.value());
        Result<InviteToken, ?> result = InviteToken.create(
                meetingId, InviteeId.of(inviteeId), UUID.randomUUID().toString(), FUTURE);
        return ((Result.Success<InviteToken, ?>) result).value();
    }
}
