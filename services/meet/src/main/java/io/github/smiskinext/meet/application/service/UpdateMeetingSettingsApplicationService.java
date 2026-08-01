package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.UpdateMeetingSettingsCommand;
import io.github.smiskinext.meet.application.mapper.UpdateMeetingSettingsMapper;
import io.github.smiskinext.meet.application.result.UpdateMeetingSettingsResult;
import io.github.smiskinext.meet.application.usecase.UpdateMeetingSettingsUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.model.AdmissionPolicy;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.ParticipantRole;
import io.github.smiskinext.meet.domain.model.ParticipationLog;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipantGrants;
import io.github.smiskinext.meet.domain.port.LiveKitPort;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.meet.domain.port.ParticipationLogRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMeetingSettingsApplicationService implements UpdateMeetingSettingsUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(UpdateMeetingSettingsApplicationService.class);

    private final MeetingRepository meetingRepository;
    private final ParticipationLogRepository participationLogRepository;
    private final EventPublisher eventPublisher;
    private final LiveKitPort liveKitPort;

    public UpdateMeetingSettingsApplicationService(
            MeetingRepository meetingRepository,
            ParticipationLogRepository participationLogRepository,
            EventPublisher eventPublisher,
            LiveKitPort liveKitPort) {
        this.meetingRepository = meetingRepository;
        this.participationLogRepository = participationLogRepository;
        this.eventPublisher = eventPublisher;
        this.liveKitPort = liveKitPort;
    }

    @Override
    @Transactional
    public Result<UpdateMeetingSettingsResult, MeetingError> execute(
            UpdateMeetingSettingsCommand command) {
        Meeting meeting =
                meetingRepository.findByIdWithLock(command.meetingId()).orElse(null);
        if (meeting == null) {
            return Result.failure(new MeetingError.MeetingNotFound(command.meetingId()));
        }

        MeetingSettings newSettings;
        try {
            newSettings = new MeetingSettings(
                    AdmissionPolicy.valueOf(command.admissionPolicy()),
                    command.maxParticipants(),
                    command.allowScreenShare(),
                    command.chatEnabled(),
                    command.allowMicrophone(),
                    command.allowVideo());
        } catch (IllegalArgumentException e) {
            return Result.failure(new MeetingError.InvalidSettings(e.getMessage()));
        }

        Result<Void, MeetingError> updateResult =
                meeting.updateSettings(AccountId.of(command.accountId()), newSettings);
        if (updateResult.isFailure()) {
            return Result.failure(((Result.Failure<Void, MeetingError>) updateResult).error());
        }

        if (!meeting.getDomainEvents().isEmpty()) {
            meetingRepository.save(meeting);
            eventPublisher.publishEventsOf(meeting);
            enforcePermissionsOnConnectedParticipants(meeting, newSettings);
        }

        return Result.success(UpdateMeetingSettingsMapper.toResult(meeting));
    }

    private void enforcePermissionsOnConnectedParticipants(
            Meeting meeting, MeetingSettings newSettings) {
        List<ParticipationLog> activeLogs =
                participationLogRepository.findActiveByMeetingId(meeting.getId().value());

        LiveKitRoomName roomName =
                LiveKitRoomName.fromMeetingId(MeetingId.of(meeting.getId().value()));

        ParticipantGrants grants = ParticipantGrants.fromSettingsForRuntimeSync(
                newSettings, ParticipantRole.PARTICIPANT);

        for (ParticipationLog participationLog : activeLogs) {
            if (participationLog.getRole() == ParticipantRole.HOST) {
                continue;
            }
            try {
                Result<Void, MeetingError> result = liveKitPort.updateParticipantPermissions(
                        roomName, participationLog.getLivekitIdentity().value(), grants);
                if (result.isFailure()) {
                    log.warn(
                            "Best-effort permission update failed for meeting={} identity={}",
                            meeting.getId().value(),
                            participationLog.getLivekitIdentity().value());
                }
            } catch (RuntimeException e) {
                log.warn(
                        "Best-effort permission update threw for meeting={} identity={}",
                        meeting.getId().value(),
                        participationLog.getLivekitIdentity().value(),
                        e);
            }
        }
    }
}
