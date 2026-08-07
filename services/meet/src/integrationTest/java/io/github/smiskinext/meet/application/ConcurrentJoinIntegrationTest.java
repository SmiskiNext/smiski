package io.github.smiskinext.meet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meet.application.command.AcceptJoinRequestsCommand;
import io.github.smiskinext.meet.application.command.RequestJoinCommand;
import io.github.smiskinext.meet.application.result.AcceptJoinRequestsResult;
import io.github.smiskinext.meet.application.result.JoinDecisionItemResult;
import io.github.smiskinext.meet.application.result.JoinDecisionStatus;
import io.github.smiskinext.meet.application.result.RequestJoinResult;
import io.github.smiskinext.meet.application.usecase.AcceptJoinRequestsUseCase;
import io.github.smiskinext.meet.application.usecase.RequestJoinUseCase;
import io.github.smiskinext.meet.config.TestcontainersConfiguration;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.MeetingErrorCode;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.port.JoinRequestRepository;
import io.github.smiskinext.meet.domain.port.JoinRequestResultStore;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Validates best-effort capacity guarantee under optimistic pre-check + pessimistic final
 * verification strategy.
 *
 * <p>Tests use real DB transactions and CountDownLatch to synchronize concurrent attempts. Because
 * participation logs are written asynchronously by webhook (not during admission), the lock
 * serializes threads but all see the same pre-join count — if 5 threads pass phase 1 before any
 * webhook fires, all 5 will pass phase 3. These tests verify lock acquisition works correctly and
 * races are detected when participation logs exist at phase 3 time.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ConcurrentJoinIntegrationTest {

    private static final String TENANT_ID = TenantContext.DEFAULT_TENANT;
    private static final String HOST_ID = "host-concurrent";

    @Autowired
    private RequestJoinUseCase requestJoinUseCase;

    @Autowired
    private AcceptJoinRequestsUseCase acceptJoinRequestsUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JoinRequestRepository joinRequestRepository;

    @Autowired
    private JoinRequestResultStore joinRequestResultStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private LiveKitPort liveKitPort;

    @BeforeEach
    void setUp() {
        insertTenant(TENANT_ID);
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
        when(liveKitPort.generateToken(any()))
                .thenAnswer(invocation -> Result.success("token-" + UUID.randomUUID()));
    }

    @Test
    void concurrentAllowAllJoinsCompleteWithoutDeadlockAndNeverExceedRecordedCapacity()
            throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 3);
        insertActiveSession(meetingId, "existing-1", "device-a");
        insertActiveSession(meetingId, "existing-2", "device-b");

        List<Result<RequestJoinResult, MeetingError>> results =
                joinConcurrently(meetingId, 5, "account-concurrent-", "device-concurrent-");

        assertThat(results).hasSize(5);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.isSuccess()
                                || failureOf(result) instanceof MeetingError.MeetingFull)
                        .isTrue());

        List<RequestJoinResult> admitted = results.stream()
                .filter(Result::isSuccess)
                .map(ConcurrentJoinIntegrationTest::successOf)
                .filter(value -> value.status() == JoinRequestStatus.APPROVED)
                .toList();

        assertThat(admitted).isNotEmpty();
        assertThat(admitted).allSatisfy(value -> {
            assertThat(value.token()).isNotBlank();
            assertThat(value.roomName()).isEqualTo("meeting-" + meetingId);
        });
        assertThat(admitted.stream().map(RequestJoinResult::token).distinct())
                .hasSize(admitted.size());

        assertThat(countActiveSessions(meetingId)).isEqualTo(2L);
    }

    @Test
    void concurrentJoinsToFullMeetingAllReceiveMeetingFull() throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 2);
        insertActiveSession(meetingId, "existing-1", "device-a");
        insertActiveSession(meetingId, "existing-2", "device-b");

        List<Result<RequestJoinResult, MeetingError>> results =
                joinConcurrently(meetingId, 5, "account-full-", "device-full-");

        assertThat(results).hasSize(5);
        assertThat(results)
                .allSatisfy(result ->
                        assertThat(failureOf(result)).isInstanceOf(MeetingError.MeetingFull.class));

        assertThat(countActiveSessions(meetingId)).isEqualTo(2L);
    }

    @Test
    void raceBetweenOptimisticAndFinalCheckYieldsMeetingFull() throws Exception {
        UUID meetingId = insertMeeting("ALLOW_ALL", 3);
        insertActiveSession(meetingId, "existing-1", "device-a");
        insertActiveSession(meetingId, "existing-2", "device-b");

        CountDownLatch tokenRequested = new CountDownLatch(1);
        CountDownLatch capacityFilled = new CountDownLatch(1);
        when(liveKitPort.generateToken(any())).thenAnswer(invocation -> {
            tokenRequested.countDown();
            capacityFilled.await(5, TimeUnit.SECONDS);
            return Result.success("race-token");
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Result<RequestJoinResult, MeetingError>> joining = executor.submit(() -> {
            TenantContext.setCurrentTenant(TENANT_ID);
            try {
                return requestJoinUseCase.execute(new RequestJoinCommand(
                        meetingId.toString(),
                        TENANT_ID,
                        "account-race",
                        "Racer",
                        "device-race",
                        "https://cdn.example.com/avatar/race.png"));
            } finally {
                TenantContext.clear();
            }
        });

        assertThat(tokenRequested.await(5, TimeUnit.SECONDS)).isTrue();
        insertActiveSession(meetingId, "late-joiner", "device-late");
        capacityFilled.countDown();

        Result<RequestJoinResult, MeetingError> result = joining.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(failureOf(result)).isInstanceOf(MeetingError.MeetingFull.class);
        assertThat(countActiveSessions(meetingId)).isEqualTo(3L);
    }

    @Test
    void concurrentSameDeviceBypassJoinsReconcilePendingRequestExactlyOnce() throws Exception {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL", 50);
        UUID pendingRequestId = createPendingJoinRequest(meetingId, "invitee-1", "device-inv-1");
        insertInvitee(meetingId, "invitee-1", "ACCEPTED");

        int threadCount = 5;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<Result<RequestJoinResult, MeetingError>> results =
                new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                TenantContext.setCurrentTenant(TENANT_ID);
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    results.add(requestJoinUseCase.execute(new RequestJoinCommand(
                            meetingId.toString(),
                            TENANT_ID,
                            "invitee-1",
                            "Invitee 1",
                            "device-inv-1",
                            "https://cdn.example.com/avatar/inv1.png")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(threadCount);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.isSuccess()).isTrue();
            assertThat(successOf(result).status()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(successOf(result).token()).isNotBlank();
        });

        assertThat(joinRequestRepository.findById(pendingRequestId)).isEmpty();
        assertThat(joinRequestRepository.findByDeviceId(meetingId, "device-inv-1"))
                .isEmpty();
        assertThat(countOutboxEvents(meetingId, "io.github.smiskinext.meet.join.approved.v1"))
                .isEqualTo(1L);
    }

    @Test
    void acceptBatchEnforcesCapacityUnderLockWithPreGeneratedTokens() {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL", 3);
        insertActiveSession(meetingId, "existing-1", "device-a");

        UUID request1 = createPendingJoinRequest(meetingId, "account-1", "device-1");
        UUID request2 = createPendingJoinRequest(meetingId, "account-2", "device-2");
        UUID request3 = createPendingJoinRequest(meetingId, "account-3", "device-3");
        UUID request4 = createPendingJoinRequest(meetingId, "account-4", "device-4");

        TenantContext.setCurrentTenant(TENANT_ID);
        Result<AcceptJoinRequestsResult, MeetingError> result;
        try {
            result = acceptJoinRequestsUseCase.execute(new AcceptJoinRequestsCommand(
                    meetingId,
                    TENANT_ID,
                    HOST_ID,
                    List.of(request1, request2, request3, request4)));
        } finally {
            TenantContext.clear();
        }

        assertThat(result.isSuccess()).isTrue();
        List<JoinDecisionItemResult> items = ((Result.Success<
                                AcceptJoinRequestsResult, MeetingError>)
                        result)
                .value()
                .results();

        assertThat(items).hasSize(4);
        assertThat(items.stream()
                        .filter(item -> item.status() == JoinDecisionStatus.APPROVED)
                        .count())
                .isEqualTo(2L);
        assertThat(items.get(3).status()).isEqualTo(JoinDecisionStatus.FAILED);
        assertThat(items.get(3).reason()).isEqualTo(MeetingErrorCode.MEETING_FULL.code());

        assertThat(items.stream()
                        .filter(item -> item.status() == JoinDecisionStatus.APPROVED)
                        .map(JoinDecisionItemResult::token)
                        .distinct())
                .hasSize(2);
    }

    @Test
    void concurrentAcceptBatchesApproveEachRequestExactlyOnce() throws Exception {
        UUID meetingId = insertMeeting("MANUAL_APPROVAL", 50);
        UUID sharedRequest = createPendingJoinRequest(meetingId, "account-shared", "device-shared");

        int threadCount = 4;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<Result<AcceptJoinRequestsResult, MeetingError>> results =
                new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                TenantContext.setCurrentTenant(TENANT_ID);
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    results.add(acceptJoinRequestsUseCase.execute(new AcceptJoinRequestsCommand(
                            meetingId, TENANT_ID, HOST_ID, List.of(sharedRequest))));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(threadCount);
        long approved = results.stream()
                .filter(Result::isSuccess)
                .map(r -> ((Result.Success<AcceptJoinRequestsResult, MeetingError>) r).value())
                .flatMap(value -> value.results().stream())
                .filter(item -> item.status() == JoinDecisionStatus.APPROVED)
                .count();

        assertThat(approved).isEqualTo(1L);
        assertThat(joinRequestRepository.findByDeviceId(meetingId, "device-shared"))
                .isEmpty();
        assertThat(countOutboxEvents(meetingId, "io.github.smiskinext.meet.join.approved.v1"))
                .isEqualTo(1L);
    }

    private UUID insertMeeting(String admissionPolicy, int maxParticipants) {
        UUID id = UUID.randomUUID();
        String settings = """
                {"admissionPolicy": "%s", "maxParticipants": %d, "allowScreenShare": true, \
                "chatEnabled": true, "allowMicrophone": true, "allowVideo": true}
                """.formatted(admissionPolicy, maxParticipants);
        jdbcTemplate.update(
                """
                INSERT INTO meetings (
                    tenant_id, id, host_id, organizer_email, organizer_display_name,
                    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
                    project_key, title, description, zone_id, type, status, settings
                ) VALUES (?, ?, ?, 'host@example.com', 'Host User', ?, 0, ?,
                    'ISS-1', 'PROJ-1', 'PROJ', 'Concurrent Test', 'Description', 'UTC',
                    'INSTANT', 'RUNNING', ?::jsonb)
                """,
                TENANT_ID,
                id,
                HOST_ID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 12),
                settings);
        return id;
    }

    private void insertActiveSession(UUID meetingId, String accountId, String deviceId) {
        jdbcTemplate.update(
                """
                INSERT INTO participation_logs (
                    tenant_id, id, meeting_id, account_id, role,
                    livekit_identity, livekit_participant_sid, joined_at, left_at, close_reason
                ) VALUES (?, ?, ?, ?, 'PARTICIPANT', ?, ?, NOW(), NULL, NULL)
                """,
                TENANT_ID,
                UUID.randomUUID(),
                meetingId,
                accountId,
                accountId + ":" + deviceId,
                "PA_" + UUID.randomUUID().toString().substring(0, 8));
    }

    private UUID insertInvitee(UUID meetingId, String accountId, String status) {
        UUID inviteeId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO meeting_invitees (
                    tenant_id, id, meeting_id, inviter_id, account_id, email,
                    display_name, role, rsvp, status, invited_at, responded_at, removed_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'Invitee User', 'REQ_PARTICIPANT',
                    TRUE, ?, NOW(), NOW(), NULL)
                """,
                TENANT_ID,
                inviteeId,
                meetingId,
                HOST_ID,
                accountId,
                accountId + "@example.com",
                status);
        return inviteeId;
    }

    private UUID createPendingJoinRequest(UUID meetingId, String accountId, String deviceId) {
        TenantContext.setCurrentTenant(TENANT_ID);
        try {
            Result<RequestJoinResult, MeetingError> result =
                    requestJoinUseCase.execute(new RequestJoinCommand(
                            meetingId.toString(),
                            TENANT_ID,
                            accountId,
                            "User " + accountId,
                            deviceId,
                            "https://cdn.example.com/avatar/" + accountId + ".png"));

            if (result instanceof Result.Success<RequestJoinResult, MeetingError> success) {
                return success.value().requestId();
            }
            throw new IllegalStateException("Failed to create pending join request");
        } finally {
            TenantContext.clear();
        }
    }

    private void insertTenant(String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tenantId);
    }

    private List<Result<RequestJoinResult, MeetingError>> joinConcurrently(
            UUID meetingId, int threadCount, String accountPrefix, String devicePrefix)
            throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<Result<RequestJoinResult, MeetingError>> results =
                new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                TenantContext.setCurrentTenant(TENANT_ID);
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    results.add(requestJoinUseCase.execute(new RequestJoinCommand(
                            meetingId.toString(),
                            TENANT_ID,
                            accountPrefix + index,
                            "Caller " + index,
                            devicePrefix + index,
                            "https://cdn.example.com/avatar/" + index + ".png")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        return List.copyOf(results);
    }

    private long countOutboxEvents(UUID meetingId, String eventType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?::uuid AND event_type = ?",
                Long.class,
                meetingId.toString(),
                eventType);
        return count == null ? 0L : count;
    }

    private long countActiveSessions(UUID meetingId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM participation_logs
                WHERE meeting_id = ?::uuid AND left_at IS NULL
                """, Long.class, meetingId.toString());
        return count == null ? 0L : count;
    }

    private static RequestJoinResult successOf(Result<RequestJoinResult, MeetingError> result) {
        return ((Result.Success<RequestJoinResult, MeetingError>) result).value();
    }

    private static MeetingError failureOf(Result<RequestJoinResult, MeetingError> result) {
        return ((Result.Failure<RequestJoinResult, MeetingError>) result).error();
    }
}
