package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.domain.MeetingError;
import io.github.smiskinext.meet.domain.event.MeetingCanceledEvent;
import io.github.smiskinext.meet.domain.model.CancelReason;
import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingInvitee;
import io.github.smiskinext.meet.domain.port.MeetingInviteeRepository;
import io.github.smiskinext.meet.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NoShowMeetingCancelerApplicationService {

    private static final Logger logger =
            LoggerFactory.getLogger(NoShowMeetingCancelerApplicationService.class);

    private final MeetingRepository meetingRepository;
    private final MeetingInviteeRepository meetingInviteeRepository;
    private final EventPublisher eventPublisher;

    public NoShowMeetingCancelerApplicationService(
            MeetingRepository meetingRepository,
            MeetingInviteeRepository meetingInviteeRepository,
            EventPublisher eventPublisher) {
        this.meetingRepository = meetingRepository;
        this.meetingInviteeRepository = meetingInviteeRepository;
        this.eventPublisher = eventPublisher;
    }

    public void cancelExpiredMeeting(UUID meetingId, String tenantId) {
        try {
            TenantContext.setCurrentTenant(tenantId);

            Meeting meeting =
                    meetingRepository.findActiveByIdWithLock(meetingId).orElse(null);
            if (meeting == null) {
                logger.debug(
                        "Meeting {} in tenant {} not found or already deleted;"
                                + " skipping no-show cancellation",
                        meetingId,
                        tenantId);
                return;
            }

            List<MeetingInvitee> allInvitees = meetingInviteeRepository.findByMeetingId(meetingId);
            List<MeetingCanceledEvent.InviteeInfo> inviteeInfos = toInviteeInfos(allInvitees);

            Result<Void, MeetingError> cancelResult = meeting.cancel(
                    CancelReason.NO_SHOW,
                    meeting.getTitle().value(),
                    meeting.getShortCode().value(),
                    meeting.getStartTime().orElse(null),
                    inviteeInfos);

            if (cancelResult.isFailure()) {
                logger.debug(
                        "Meeting {} in tenant {} cannot be canceled (already transitioned);"
                                + " skipping",
                        meetingId,
                        tenantId);
                return;
            }

            meetingRepository.save(meeting);
            eventPublisher.publishEventsOf(meeting);

            logger.info(
                    "Meeting {} in tenant {} auto-canceled (NO_SHOW) with {} invitees",
                    meetingId,
                    tenantId,
                    inviteeInfos.size());
        } finally {
            TenantContext.clear();
        }
    }

    public void cancelAllExpired(int batchSize) {
        Instant cutoff = Instant.now();
        List<MeetingRepository.MeetingIdAndTenant> expiredMeetings =
                meetingRepository.findScheduledExpiredAcrossTenants(batchSize, cutoff);

        logger.info(
                "No-show canceler: found {} expired SCHEDULED meetings (cutoff: {})",
                expiredMeetings.size(),
                cutoff);

        for (MeetingRepository.MeetingIdAndTenant ref : expiredMeetings) {
            cancelExpiredMeeting(ref.id(), ref.tenantId());
        }
    }

    private List<MeetingCanceledEvent.InviteeInfo> toInviteeInfos(List<MeetingInvitee> invitees) {
        List<MeetingCanceledEvent.InviteeInfo> infos = new ArrayList<>();
        for (MeetingInvitee invitee : invitees) {
            if (invitee.getRemovedAt().isEmpty()) {
                infos.add(new MeetingCanceledEvent.InviteeInfo(
                        invitee.getAccountId().value(),
                        invitee.getEmail().value(),
                        invitee.getDisplayName().value(),
                        invitee.getStatus().name(),
                        invitee.getInvitedAt()));
            }
        }
        return infos;
    }
}
