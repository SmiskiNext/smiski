package io.github.smiskinext.usermanagement.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserDomainEventsTest {

    @Test
    void userRegisteredEvent_hasCorrectFields() {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();

        var event = new UserRegisteredEvent(
                eventId, userId.value(), "alice@example.com", "Alice", "alice_user", now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.aggregateId()).isEqualTo(userId.value());
        assertThat(event.userId()).isEqualTo(userId.value());
        assertThat(event.email()).isEqualTo("alice@example.com");
        assertThat(event.fullName()).isEqualTo("Alice");
        assertThat(event.registeredAt()).isEqualTo(now);
        assertThat(event.aggregateType()).isEqualTo("user");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.user.registered.v1");
        assertThat(event.topic()).isEqualTo("user-management.user.registered");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void userLoggedInEvent_hasCorrectFields() {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();

        var event = new UserLoggedInEvent(eventId, userId.value(), "bob@example.com", now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.aggregateId()).isEqualTo(userId.value());
        assertThat(event.email()).isEqualTo("bob@example.com");
        assertThat(event.loginAt()).isEqualTo(now);
        assertThat(event.aggregateType()).isEqualTo("user");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.user.logged-in.v1");
        assertThat(event.topic()).isEqualTo("user-management.user.logged-in");
    }

    @Test
    void userDeletedEvent_hasCorrectFields() {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();

        var event = new UserDeletedEvent(eventId, userId.value(), "carol@example.com", now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.aggregateId()).isEqualTo(userId.value());
        assertThat(event.email()).isEqualTo("carol@example.com");
        assertThat(event.deletedAt()).isEqualTo(now);
        assertThat(event.aggregateType()).isEqualTo("user");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.user.deleted.v1");
        assertThat(event.topic()).isEqualTo("user-management.user.deleted");
    }

    @Test
    void userUpdatedEvent_hasCorrectFields() {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();

        var event = new UserUpdatedEvent(
                eventId,
                userId.value(),
                "dave@example.com",
                "Dave",
                "dave_user",
                "https://example.com/avatar.png",
                "EMAIL",
                now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.aggregateId()).isEqualTo(userId.value());
        assertThat(event.email()).isEqualTo("dave@example.com");
        assertThat(event.fullName()).isEqualTo("Dave");
        assertThat(event.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(event.authProvider()).isEqualTo("EMAIL");
        assertThat(event.updatedAt()).isEqualTo(now);
        assertThat(event.aggregateType()).isEqualTo("user");
        assertThat(event.eventType()).isEqualTo("io.github.phunguy65.zms.user.updated.v1");
        assertThat(event.topic()).isEqualTo("user-management.user.updated");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void userUpdatedEvent_nullAvatarUrl_isAllowed() {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        UserId userId = UserId.of(UuidCreator.getTimeOrderedEpoch());
        Instant now = Instant.now();

        var event = new UserUpdatedEvent(
                eventId, userId.value(), "eve@example.com", "Eve", null, null, "GOOGLE", now);

        assertThat(event.avatarUrl()).isNull();
    }
}
