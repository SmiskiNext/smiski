package io.github.smiskinext.meetingmanagement.application.helper;

import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.port.MeetingRepository;
import io.github.smiskinext.shared.domain.Result;
import org.springframework.stereotype.Service;

/**
 * Generates unique {@link ShortCode} values with collision-retry logic.
 *
 * <p>Retries up to {@code maxAttempts} times before returning
 * {@link MeetingError.ShortCodeExhausted}.
 */
@Service
public class ShortCodeGenerator {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;

    private final MeetingRepository meetingRepository;

    public ShortCodeGenerator(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    /**
     * Generates a unique short code, retrying up to {@value DEFAULT_MAX_ATTEMPTS} times.
     *
     * @return {@link Result.Success} with a unique {@link ShortCode}, or
     * {@link Result.Failure} with {@link MeetingError.ShortCodeExhausted} if all attempts
     * collide
     */
    public Result<ShortCode, MeetingError> generate() {
        for (int attempt = 0; attempt < DEFAULT_MAX_ATTEMPTS; attempt++) {
            ShortCode candidate = ShortCode.generate();
            if (!meetingRepository.existsByShortCode(candidate)) {
                return Result.success(candidate);
            }
        }
        return Result.failure(new MeetingError.ShortCodeExhausted());
    }
}
