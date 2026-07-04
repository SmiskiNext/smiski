package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.notification.infrastructure.messaging.PasswordResetRequestedMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetEmailRendererTest {

    private final PasswordResetEmailRenderer renderer = new PasswordResetEmailRenderer();

    @Test
    void renderSubject_isYourPasswordResetCode() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.subject()).isEqualTo("Your Password Reset Code");
    }

    @Test
    void renderHtml_containsOtpProminentlyDisplayed() {
        PasswordResetRequestedMessage message = createMessage("482951", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("482951");
        // OTP should be in a styled span with large font
        assertThat(result.html()).contains("font-size:32px");
        assertThat(result.html()).contains("letter-spacing:8px");
    }

    @Test
    void renderHtml_contains15MinuteExpiryNotice() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("15 minutes");
    }

    @Test
    void renderHtml_containsWarningNotToShareCode() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).containsIgnoringCase("never share this code");
        assertThat(result.html()).containsIgnoringCase("Important");
    }

    @Test
    void renderHtml_containsUserFullName() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice Smith");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("Hello Alice Smith");
    }

    @Test
    void renderHtml_escapesPotentialXssInFullName() {
        PasswordResetRequestedMessage message =
                createMessage("123456", "<script>alert('xss')</script>");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("&lt;script&gt;");
    }

    @Test
    void renderHtml_usesDefaultNameWhenFullNameIsBlank() {
        PasswordResetRequestedMessage message = createMessage("123456", "");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("Hello there");
    }

    @Test
    void renderHtml_usesDefaultNameWhenFullNameIsNull() {
        PasswordResetRequestedMessage message = new PasswordResetRequestedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                null, // null fullName
                "123456",
                Instant.now().plusSeconds(900),
                Instant.now());

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("Hello there");
    }

    @Test
    void renderHtml_containsPasswordResetRequestHeading() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).contains("Password Reset Request");
    }

    @Test
    void renderHtml_containsIgnoreMessageIfNotRequested() {
        PasswordResetRequestedMessage message = createMessage("123456", "Alice");

        PasswordResetEmailRenderer.RenderedEmail result = renderer.render(message);

        assertThat(result.html()).containsIgnoringCase("if you didn't request");
        assertThat(result.html()).containsIgnoringCase("safely ignore");
    }

    private PasswordResetRequestedMessage createMessage(String otp, String fullName) {
        return new PasswordResetRequestedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                fullName,
                otp,
                Instant.now().plusSeconds(900),
                Instant.now());
    }
}
