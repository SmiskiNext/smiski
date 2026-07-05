package io.github.smiskinext.usermanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.valueobject.Email;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.event.UserUpdatedEvent;
import io.github.smiskinext.usermanagement.domain.model.valueobject.FullName;
import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserUpdateProfileTest {

    private User buildUser(String avatarUrl) {
        return User.reconstitute(
                UserId.of(UuidCreator.getTimeOrderedEpoch()),
                Email.of("alice@example.com"),
                null,
                FullName.of("Alice"),
                null,
                avatarUrl,
                null,
                "EMAIL",
                null,
                Instant.now(),
                Instant.now(),
                null);
    }

    @Test
    void replaceProfile_updatesAllFieldsAndRegistersEvent() {
        var user = buildUser(null);

        user.replaceProfile(
                FullName.of("New Name"),
                new AvatarUpdate.Set("https://example.com/avatar.png"),
                Username.of("new_name"));

        assertThat(user.getFullName().value()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).contains("https://example.com/avatar.png");
        assertThat(user.getUsername()).contains(Username.of("new_name"));
        assertThat(user.getDomainEvents()).hasSize(1);
        assertThat(user.getDomainEvents().get(0)).isInstanceOf(UserUpdatedEvent.class);
        var event = (UserUpdatedEvent) user.getDomainEvents().get(0);
        assertThat(event.fullName()).isEqualTo("New Name");
        assertThat(event.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(event.username()).isEqualTo("new_name");
    }

    @Test
    void replaceProfile_clearAvatarUrl_setsNullAndRegistersEvent() {
        var user = buildUser("https://example.com/old.png");

        user.replaceProfile(FullName.of("Alice"), new AvatarUpdate.Clear(), Username.of("alice"));

        assertThat(user.getAvatarUrl()).isEmpty();
        assertThat(user.getDomainEvents()).hasSize(1);
        var event = (UserUpdatedEvent) user.getDomainEvents().get(0);
        assertThat(event.avatarUrl()).isNull();
    }

    @Test
    void replaceProfile_updatesUpdatedAt() throws InterruptedException {
        var user = buildUser(null);
        Instant before = user.getUpdatedAt();
        Thread.sleep(1);

        user.replaceProfile(
                FullName.of("Updated"), new AvatarUpdate.Clear(), Username.of("updated"));

        assertThat(user.getUpdatedAt()).isAfter(before);
    }

    @Test
    void replaceProfile_eventCarriesCorrectAuthProvider() {
        var user = buildUser(null);

        user.replaceProfile(FullName.of("Alice"), new AvatarUpdate.Clear(), Username.of("alice"));

        var event = (UserUpdatedEvent) user.getDomainEvents().get(0);
        assertThat(event.authProvider()).isEqualTo("EMAIL");
        assertThat(event.email()).isEqualTo("alice@example.com");
    }
}
