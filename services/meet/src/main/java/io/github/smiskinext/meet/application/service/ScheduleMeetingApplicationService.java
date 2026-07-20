package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.ScheduleMeetingCommand;
import io.github.smiskinext.meet.application.helper.ShortCodeAllocator;
import io.github.smiskinext.meet.application.result.ScheduleMeetingResult;
import io.github.smiskinext.meet.application.usecase.ScheduleMeetingUseCase;
import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingInvitationsCreatedEvent;
import io.github.smiskinext.meet.domain.model.*;
import io.github.smiskinext.meet.domain.model.valueobject.*;
import io.github.smiskinext.meet.domain.port.InviteTokenGenerator;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.TenantId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScheduleMeetingApplicationService implements ScheduleMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;
    private final InviteTokenGenerator inviteTokenGenerator;
    private final ShortCodeAllocator shortCodeAllocator;

    public ScheduleMeetingApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher,
            InviteTokenGenerator inviteTokenGenerator,
            ShortCodeAllocator shortCodeAllocator) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
        this.inviteTokenGenerator = inviteTokenGenerator;
        this.shortCodeAllocator = shortCodeAllocator;
    }

    @Override
    public Result<ScheduleMeetingResult, MeetingError> execute(ScheduleMeetingCommand command) {
        return shortCodeAllocator
                .allocate(shortCode -> createMeeting(command, shortCode))
                .orElseGet(() -> Result.failure(new MeetingError.ShortCodeExhausted()));
    }

    private Result<ScheduleMeetingResult, MeetingError> createMeeting(
            ScheduleMeetingCommand command, ShortCode shortCode) {

        TenantId tenantId = TenantId.of(command.tenantId());
        AccountId hostAccountId = AccountId.of(command.hostAccountId());

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

        MeetingTimeRange timeRange = MeetingTimeRange.of(
                command.timeRange().start(), command.timeRange().end());

        MeetingTimeZone timeZone = MeetingTimeZone.of(command.zoneId());

        Result<Meeting, MeetingError> scheduleResult = Meeting.schedule(
                tenantId,
                hostAccountId,
                title,
                command.description(),
                issueLink,
                timeRange,
                settings,
                timeZone,
                Email.of(command.organizerEmail()),
                InviteeDisplayName.of(command.organizerDisplayName()),
                shortCode);

        if (scheduleResult instanceof Result.Failure<Meeting, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }

        Meeting meeting = ((Result.Success<Meeting, MeetingError>) scheduleResult).value();

        List<MeetingInvitee> invitees = new ArrayList<>();
        List<MeetingInvitationsCreatedEvent.InviteeInfo> inviteeInfos = new ArrayList<>();

        if (command.invitees() != null && !command.invitees().isEmpty()) {
            for (ScheduleMeetingCommand.Invitee inviteeCmd : command.invitees()) {
                MeetingInvitee invitee = MeetingInvitee.create(
                        tenantId,
                        meeting.getId(),
                        InviterId.of(hostAccountId.value()),
                        AccountId.of(inviteeCmd.accountId()),
                        Email.of(inviteeCmd.email()),
                        InviteeDisplayName.of(inviteeCmd.displayName()));

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

        meetingRepository.save(meeting);
        if (!invitees.isEmpty()) {
            meetingInviteeRepository.saveAll(invitees);
        }

        eventPublisher.publishEventsOf(meeting);

        ScheduleMeetingResult result = buildResult(meeting, timeRange);
        return Result.success(result);
    }

    private ScheduleMeetingResult buildResult(Meeting meeting, MeetingTimeRange timeRange) {
        JiraIssueLink link = meeting.getIssueLink();
        ScheduleMeetingResult.IssueLink issueLink = new ScheduleMeetingResult.IssueLink(
                link.issueId(), link.issueKey(), link.projectKey());

        MeetingSettings s = meeting.getSettings();
        ScheduleMeetingResult.Settings settings = new ScheduleMeetingResult.Settings(
                s.admissionPolicy().name(),
                s.maxParticipants(),
                s.allowScreenShare(),
                s.chatEnabled(),
                s.allowMicrophone(),
                s.allowVideo());

        return new ScheduleMeetingResult(
                meeting.getId().value(),
                meeting.getHostId().value(),
                meeting.getShortCode().value(),
                meeting.getType().name(),
                meeting.getStatus().name(),
                meeting.getTitle().value(),
                meeting.getDescription(),
                issueLink,
                settings,
                timeRange.start(),
                timeRange.end(),
                meeting.getTimeZone().value(),
                meeting.getOrganizerEmail().value(),
                meeting.getOrganizerDisplayName().value(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence(),
                meeting.getCreatedAt());
    }
}
