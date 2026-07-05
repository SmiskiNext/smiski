package io.github.smiskinext.meetingmanagement.application.usecase;

import io.github.smiskinext.meetingmanagement.application.command.CreateInstantMeetingCommand;
import io.github.smiskinext.meetingmanagement.application.helper.MeetingSettingsPasswordResolver;
import io.github.smiskinext.meetingmanagement.application.helper.ShortCodeGenerator;
import io.github.smiskinext.meetingmanagement.application.response.MeetingResponse;
import io.github.smiskinext.meetingmanagement.application.response.MeetingSettingsResponse;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.MeetingTitle;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.meetingmanagement.domain.port.PasswordHasher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateInstantMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordHasher passwordHasher;

    public CreateInstantMeetingUseCase(
            MeetingRepository meetingRepository,
            ShortCodeGenerator shortCodeGenerator,
            ApplicationEventPublisher eventPublisher,
            PasswordHasher passwordHasher) {
        this.meetingRepository = meetingRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.eventPublisher = eventPublisher;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public Result<MeetingResponse, MeetingError> execute(CreateInstantMeetingCommand command) {
        var shortCodeResult = shortCodeGenerator.generate();
        if (shortCodeResult instanceof Result.Failure<?, MeetingError>(MeetingError error)) {
            return Result.failure(error);
        }
        var shortCode = ((Result.Success<ShortCode, MeetingError>) shortCodeResult).value();

        MeetingSettings settings = command.settings();
        String rawPassword =
                MeetingSettingsPasswordResolver.normalizeRawPassword(command.rawPassword());
        if (rawPassword != null) {
            settings = MeetingSettingsPasswordResolver.withRawPassword(
                    settings, rawPassword, passwordHasher);
        }

        Meeting meeting = Meeting.instant(
                UserId.of(command.hostId()), command.title(), null, settings, shortCode);

        Meeting saved = meetingRepository.save(meeting);

        saved.getDomainEvents().stream()
                .filter(e -> e instanceof PublishableEvent)
                .map(e -> (PublishableEvent) e)
                .forEach(eventPublisher::publishEvent);
        saved.clearDomainEvents();

        return Result.success(toResponse(saved));
    }

    private MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(
                m.getId().value(),
                m.getHostId().value(),
                m.getShortCode().value(),
                m.getTitle().map(MeetingTitle::value).orElse(null),
                m.getDescription().orElse(null),
                m.getStartTime().orElse(null),
                m.getEndTime().orElse(null),
                m.getType(),
                m.getStatus(),
                MeetingSettingsResponse.from(m.getSettings()),
                m.getCreatedAt());
    }
}
