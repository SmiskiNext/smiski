package io.github.smiskinext.usermanagement.application.usecase.internal;

import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal use case for batch-resolving users by email.
 *
 * <p>Returns a map of email → {@link UserSummary} for active (non-deleted) users only.
 * Missing or invalid emails are absent from the result map (partial results).
 */
@Service
public class BatchGetUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(BatchGetUserUseCase.class);

    private final UserRepository userRepository;

    public BatchGetUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, UserSummary> execute(List<String> emails) {
        if (emails == null || emails.isEmpty()) return Map.of();

        List<String> normalized = emails.stream()
                .filter(e -> e != null && !e.isBlank())
                .<String>mapMulti((raw, downstream) -> {
                    try {
                        downstream.accept(Email.of(raw).value());
                    } catch (IllegalArgumentException ignored) {
                        log.warn("Skipping invalid email in batch get: '{}'", raw);
                    }
                })
                .distinct()
                .toList();

        if (normalized.isEmpty()) return Map.of();

        return userRepository.findSummariesByEmails(normalized).stream()
                .collect(Collectors.toMap(
                        UserSummary::email, s -> s, (a, b) -> a, LinkedHashMap::new));
    }
}
