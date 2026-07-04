package io.github.smiskinext.usermanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.smiskinext.usermanagement.domain.model.valueobject.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "alice",
                "alice_smith",
                "alice-smith",
                "AliceSmith123",
                "abc",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void validUsernames_areAccepted(String value) {
        var username = Username.of(value);
        assertThat(username.value()).isEqualTo(value);
    }

    @Test
    void of_trimsNothing_valueIsExact() {
        var username = Username.of("alice");
        assertThat(username.value()).isEqualTo("alice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a", ""})
    void tooShort_throwsIllegalArgument(String value) {
        assertThatThrownBy(() -> Username.of(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tooLong_throwsIllegalArgument() {
        String longValue = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 31 chars
        assertThatThrownBy(() -> Username.of(longValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice smith", "alice@smith", "alice.smith", "alice!", "alice/smith"})
    void invalidCharacters_throwsIllegalArgument(String value) {
        assertThatThrownBy(() -> Username.of(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> Username.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateForGoogle_producesValidUsername() {
        var username = Username.generateForGoogle();
        assertThat(username.value()).startsWith("user_");
        assertThat(username.value()).hasSize(13); // "user_" + 8 chars
        // Should be valid (no exception thrown)
        Username.of(username.value());
    }

    @Test
    void generateForGoogle_producesUniqueValues() {
        var u1 = Username.generateForGoogle();
        var u2 = Username.generateForGoogle();
        // Extremely unlikely to collide
        assertThat(u1.value()).isNotEqualTo(u2.value());
    }

    @Test
    void equality_sameValue_areEqual() {
        assertThat(Username.of("alice")).isEqualTo(Username.of("alice"));
    }

    @Test
    void equality_differentValue_areNotEqual() {
        assertThat(Username.of("alice")).isNotEqualTo(Username.of("bob_x"));
    }
}
