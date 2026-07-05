package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.query.GetPendingInvitationsQuery;
import io.github.smiskinext.meetingmanagement.application.response.PendingInvitationResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.UserGrpcServicePort;
import io.github.smiskinext.shared.domain.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPendingInvitationsUseCase {

    private final MeetingInviteeRepository meetingInviteeRepository;
    private final MeetingRepository meetingRepository;
    private final UserGrpcServicePort userGrpcServicePort;

    public GetPendingInvitationsUseCase(
            MeetingInviteeRepository meetingInviteeRepository,
            MeetingRepository meetingRepository,
            UserGrpcServicePort userGrpcServicePort) {
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.meetingRepository = meetingRepository;
        this.userGrpcServicePort = userGrpcServicePort;
    }

    @Transactional(readOnly = true)
    public Result<List<PendingInvitationResponse>, MeetingError> execute(
            GetPendingInvitationsQuery query) {
        if (!query.requesterId().equals(query.userId())) {
            return Result.failure(new MeetingError.NotOwner(query.requesterId(), query.userId()));
        }

        var invitees = meetingInviteeRepository.findPendingByUserId(query.userId());
        Map<UUID, UserGrpcServicePort.ResolvedUser> hosts = resolveHosts(invitees.stream()
                .map(invitee -> invitee.getInviterId().value())
                .distinct()
                .toList());

        List<PendingInvitationResponse> responses = new ArrayList<>();
        for (var invitee : invitees) {
            meetingRepository.findById(invitee.getMeetingId().value()).ifPresent(meeting -> {
                String hostDisplayName =
                        hosts.containsKey(invitee.getInviterId().value())
                                ? hosts.get(invitee.getInviterId().value()).displayName()
                                : invitee.getEmail().value();
                responses.add(PendingInvitationResponse.from(invitee, meeting, hostDisplayName));
            });
        }
        return Result.success(List.copyOf(responses));
    }

    private Map<UUID, UserGrpcServicePort.ResolvedUser> resolveHosts(List<UUID> hostIds) {
        if (hostIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userGrpcServicePort.batchGetUsersByIds(hostIds);
        } catch (UserGrpcServicePort.UserServiceException e) {
            return Map.of();
        }
    }
}
