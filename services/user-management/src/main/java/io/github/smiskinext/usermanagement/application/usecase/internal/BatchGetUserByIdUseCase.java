package io.github.smiskinext.usermanagement.application.usecase.internal;

import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchGetUserByIdUseCase {

    private static final Logger log = LoggerFactory.getLogger(BatchGetUserByIdUseCase.class);

    private final UserRepository userRepository;

    public BatchGetUserByIdUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> execute(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        List<UserId> normalized = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .<UserId>mapMulti((raw, downstream) -> {
                    try {
                        downstream.accept(UserId.of(UUID.fromString(raw)));
                    } catch (IllegalArgumentException ignored) {
                        log.warn("Skipping invalid user id in batch get: '{}'", raw);
                    }
                })
                .distinct()
                .toList();

        if (normalized.isEmpty()) return Map.of();

        return userRepository.findSummariesByIds(normalized).stream()
                .collect(
                        Collectors.toMap(UserSummary::id, s -> s, (a, b) -> a, LinkedHashMap::new));
    }
}
