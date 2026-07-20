package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meet.application.helper.ShortCodeAllocator;
import io.github.smiskinext.meet.application.result.CreateInstantMeetingResult;
import io.github.smiskinext.meet.application.usecase.CreateInstantMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.model.*;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.*;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CreateInstantMeetingApplicationService implements CreateInstantMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;
    private final LiveKitPort liveKitPort;
    private final InviteTokenGenerator inviteTokenGenerator;
    private final ShortCodeAllocator shortCodeAllocator;

    public CreateInstantMeetingApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher,
            LiveKitPort liveKitPort,
            InviteTokenGenerator inviteTokenGenerator,
            ShortCodeAllocator shortCodeAllocator) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
        this.liveKitPort = liveKitPort;
        this.inviteTokenGenerator = inviteTokenGenerator;
        this.shortCodeAllocator = shortCodeAllocator;
    }

    @Override
    public Result<CreateInstantMeetingResult, MeetingError> execute(
            CreateInstantMeetingCommand command) {
        return shortCodeAllocator
                .allocate(shortCode -> createMeeting(command, shortCode))
                .orElseGet(() -> Result.failure(new MeetingError.ShortCodeExhausted()));
    }

    private Result<CreateInstantMeetingResult, MeetingError> createMeeting(
            CreateInstantMeetingCommand command, ShortCode shortCode) {

        TenantId tenantId = TenantId.of(command.tenantId());
        AccountId hostAccountId = AccountId.of(command.host().accountId());

        MeetingSettings settings = new MeetingSettings(
                AdmissionPolicy.valueOf(command.settings().admissionPolicy()),
                command.settings().maxParticipants(),
                command.settings().allowScreenShare(),
                command.settings().chatEnabled(),
                command.settings().allowMicrophone(),
                command.settings().allowVideo());

        JiraIssueLink issueLink = JiraIssueLink.of(
                command.issueLink().issueId(),
                command.issueLink().issueKey(),
                command.issueLink().projectKey());

        MeetingTitle title = MeetingTitle.of(command.title());

        MeetingTimeZone timeZone = MeetingTimeZone.of(command.zoneId());

        Meeting meeting = Meeting.instant(
                tenantId,
                hostAccountId,
                title,
                command.description(),
                issueLink,
                settings,
                timeZone,
                Email.of(command.organizerEmail()),
                InviteeDisplayName.of(command.organizerDisplayName()),
                shortCode);

        Result<Void, MeetingError> startResult = meeting.start();
        if (startResult instanceof Result.Failure<Void, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        LiveKitIdentity hostIdentity =
                LiveKitIdentity.fromAccount(hostAccountId, command.host().deviceId());

        List<MeetingInvitee> invitees = new ArrayList<>();
        List<MeetingInvitationsCreatedEvent.InviteeInfo> inviteeInfos = new ArrayList<>();

        if (command.invitees() != null && !command.invitees().isEmpty()) {
            for (CreateInstantMeetingCommand.Invitee inviteeCmd : command.invitees()) {
                MeetingInvitee invitee = MeetingInvitee.create(
                        tenantId,
                        meeting.getId(),
                        InviterId.of(hostAccountId.value()),
                        AccountId.of(inviteeCmd.accountId()),
                        Email.of(inviteeCmd.email()),
                        InviteeDisplayName.of(inviteeCmd.displayName()),
                        InviteeRole.REQ_PARTICIPANT,
                        true);

                InviteTokenGenerator.TokenResult tokenResult = inviteTokenGenerator.generate();

                invitee.assignToken(tokenResult.tokenHash(), tokenResult.expiresAt());

                invitees.add(invitee);

                inviteeInfos.add(new MeetingInvitationsCreatedEvent.InviteeInfo(
                        invitee.getId().value(),
                        inviteeCmd.accountId(),
                        inviteeCmd.email(),
                        inviteeCmd.displayName(),
                        invitee.getStatus().name(),
                        tokenResult.rawToken()));
            }

            meeting.recordInvitationsSent(inviteeInfos);
        }

        LiveKitRoomName roomName = LiveKitRoomName.fromMeetingId(meeting.getId());
        ParticipantAttributes hostAttributes =
                new ParticipantAttributes(command.host().avatarUrl(), ParticipantRole.HOST);
        LiveKitTokenRequest tokenRequest = new LiveKitTokenRequest(
                roomName,
                hostIdentity,
                command.host().displayName(),
                ParticipantRole.HOST,
                hostAttributes,
                settings);

        Result<String, MeetingError> tokenResult = liveKitPort.generateToken(tokenRequest);
        if (tokenResult instanceof Result.Failure<String, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        String livekitToken = ((Result.Success<String, MeetingError>) tokenResult).value();

        meetingRepository.save(meeting);
        if (!invitees.isEmpty()) {
            meetingInviteeRepository.saveAll(invitees);
        }

        eventPublisher.publishEventsOf(meeting);

        CreateInstantMeetingResult result = buildResult(meeting, livekitToken, roomName);
        return Result.success(result);
    }

    private CreateInstantMeetingResult buildResult(
            Meeting meeting, String livekitToken, LiveKitRoomName roomName) {
        JiraIssueLink link = meeting.getIssueLink();
        CreateInstantMeetingResult.IssueLink issueLink = new CreateInstantMeetingResult.IssueLink(
                link.issueId(), link.issueKey(), link.projectKey());

        MeetingSettings s = meeting.getSettings();
        CreateInstantMeetingResult.Settings settings = new CreateInstantMeetingResult.Settings(
                s.admissionPolicy().name(),
                s.maxParticipants(),
                s.allowScreenShare(),
                s.chatEnabled(),
                s.allowMicrophone(),
                s.allowVideo());

        return new CreateInstantMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                issueLink,
                settings,
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCreatedAt(),
                new CreateInstantMeetingResult.LiveKit(livekitToken, roomName.value()));
    }
}
