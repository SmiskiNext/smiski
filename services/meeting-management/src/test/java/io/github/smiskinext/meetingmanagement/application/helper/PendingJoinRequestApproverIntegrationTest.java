package io.github.smiskinext.meetingmanagement.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.smiskinext.meetingmanagement.config.RedisTestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.phunguy65.zms.meetingmanagement.domain.model.*;
import io.github.smiskinext.meetingmanagement.domain.model.AdmissionPolicy;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitIdentity;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitTokenRequest;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.JoinRequestRepository;
import io.github.smiskinext.meetingmanagement.domain.port.LiveKitPort;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link PendingJoinRequestApprover} verifying partial failure handling
 * in a full Spring context with real Redis and PostgreSQL persistence.
 *
 * <p>The {@link LiveKitPort} and {@link UserGrpcServicePort} are mocked to control token
 * generation outcomes. All other beans (repository adapters, event publisher) are real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class PendingJoinRequestApproverIntegrationTest {

    @Autowired
    PendingJoinRequestApprover approver;

    @Autowired
    JoinRequestRepository joinRequestRepository;

    @Autowired
    MeetingRepository meetingRepository;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    LiveKitPort liveKitPort;

    @MockitoBean
    UserGrpcServicePort userGrpcServicePort;

    private static final Duration REQUEST_TTL = Duration.ofMinutes(5);

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    void approveAll_oneTokenFailure_returnsPartialApprovalFailure() {
        UUID hostId = UUID.randomUUID();
        Meeting meeting = createAndSaveLiveMeeting(hostId);
        UUID meetingId = meeting.getId().value();

        JoinRequest r1 = createAndSaveRequest(meetingId);
        JoinRequest r2 = createAndSaveRequest(meetingId);
        JoinRequest r3 = createAndSaveRequest(meetingId);

        when(userGrpcServicePort.batchGetUsersByIds(any())).thenReturn(Map.of());

        LiveKitIdentity failIdentity =
                LiveKitIdentity.fromUser(r2.getUserId().orElseThrow(), r2.getDeviceId());
        when(liveKitPort.generateToken(any())).thenAnswer(invocation -> {
            LiveKitTokenRequest req = invocation.getArgument(0);
            if (req.identity().equals(failIdentity)) {
                return Result.failure(new MeetingError.LiveKitUnavailable("connection refused"));
            }
            return Result.success("valid-token");
        });

        Result<Integer, MeetingError> result = approver.approveAll(meeting, hostId);

        assertThat(result.isFailure()).isTrue();
        MeetingError error = ((Result.Failure<Integer, MeetingError>) result).error();
        assertThat(error).isInstanceOf(MeetingError.PartialApprovalFailure.class);

        var partial = (MeetingError.PartialApprovalFailure) error;
        assertThat(partial.approvedCount()).isEqualTo(2);
        assertThat(partial.failedIds()).containsExactly(r2.getId().value());
    }

    @Test
    void approveAll_allSucceed_returnsSuccessCount() {
        UUID hostId = UUID.randomUUID();
        Meeting meeting = createAndSaveLiveMeeting(hostId);
        UUID meetingId = meeting.getId().value();

        createAndSaveRequest(meetingId);
        createAndSaveRequest(meetingId);

        when(userGrpcServicePort.batchGetUsersByIds(any())).thenReturn(Map.of());
        when(liveKitPort.generateToken(any())).thenReturn(Result.success("valid-token"));

        Result<Integer, MeetingError> result = approver.approveAll(meeting, hostId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Result.Success<Integer, MeetingError>) result).value()).isEqualTo(2);
    }

    @Test
    void approveAll_allFail_returnsPartialFailureWithZeroApproved() {
        UUID hostId = UUID.randomUUID();
        Meeting meeting = createAndSaveLiveMeeting(hostId);
        UUID meetingId = meeting.getId().value();

        JoinRequest r1 = createAndSaveRequest(meetingId);
        JoinRequest r2 = createAndSaveRequest(meetingId);

        when(userGrpcServicePort.batchGetUsersByIds(any())).thenReturn(Map.of());
        when(liveKitPort.generateToken(any()))
                .thenReturn(Result.failure(new MeetingError.LiveKitUnavailable("down")));

        Result<Integer, MeetingError> result = approver.approveAll(meeting, hostId);

        assertThat(result.isFailure()).isTrue();
        var partial = (MeetingError.PartialApprovalFailure)
                ((Result.Failure<Integer, MeetingError>) result).error();
        assertThat(partial.approvedCount()).isEqualTo(0);
        assertThat(partial.failedIds()).hasSize(2);
        assertThat(partial.failedIds())
                .containsExactlyInAnyOrder(r1.getId().value(), r2.getId().value());
    }

    private Meeting createAndSaveLiveMeeting(UUID hostId) {
        MeetingSettings settings = new MeetingSettings(
                AdmissionPolicy.MANUAL_APPROVAL, true, 100, true, true, true, true, null);
        Meeting meeting = Meeting.instant(
                UserId.of(hostId),
                null,
                null,
                settings,
                ShortCode.of("TEST" + hostId.toString().substring(0, 4)));
        meeting.start();
        return meetingRepository.save(meeting);
    }

    private JoinRequest createAndSaveRequest(UUID meetingId) {
        JoinRequest request = JoinRequest.create(
                MeetingId.of(meetingId),
                UserId.of(UUID.randomUUID()),
                "User-" + UUID.randomUUID().toString().substring(0, 6),
                UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofMinutes(5)));
        joinRequestRepository.save(request, REQUEST_TTL);
        return request;
    }
}
