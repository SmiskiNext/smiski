package io.github.smiskinext.notification.domain.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserLookupPort {

    Map<UUID, UserInfo> findUsersByIds(List<UUID> userIds);

    record UserInfo(UUID id, String email, String fullName) {}
}
