package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.notification.infrastructure.messaging.MeetingInvitationsSentMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingInvitationEmailRendererTest {

    private final MeetingInvitationEmailRenderer renderer = new MeetingInvitationEmailRenderer();

    @Test
    void rendersEmailWithRequiredMetadata() {
        var rendered = renderer.render(
                invitation("Planning Session", Instant.parse("2026-04-03T10:00:00Z")),
                invitee("Alice"),
                "https://app.example.com/join?token=abc123");

        assertThat(rendered.subject()).isEqualTo("Invitation: Planning Session");
        assertThat(rendered.html())
                .contains("Hello Alice")
                .contains("You're invited to Planning Session")
                .contains("Fri, 03 Apr 2026 at 10:00 UTC")
                .contains("https://app.example.com/join?token=abc123")
                .doesNotContain("password");
    }

    @Test
    void usesFallbackCopyForMissingOptionalFields() {
        var rendered = renderer.render(
                invitation(null, null),
                invitee(null),
                "https://app.example.com/join?token=fallback");

        assertThat(rendered.subject()).isEqualTo("Invitation: your Zero Meeting System meeting");
        assertThat(rendered.html())
                .contains("Hello there")
                .contains("your Zero Meeting System meeting")
                .contains("The host will start the meeting soon.");
    }

    @Test
    void usesFallbackCopyForBlankOptionalFields() {
        var rendered = renderer.render(
                invitation("   ", null),
                invitee("   "),
                "https://app.example.com/join?token=fallback");

        assertThat(rendered.subject()).isEqualTo("Invitation: your Zero Meeting System meeting");
        assertThat(rendered.html())
                .contains("Hello there")
                .contains("your Zero Meeting System meeting");
    }

    @Test
    void escapesUserControlledFields() {
        var rendered = renderer.render(
                invitation("<script>alert(1)</script>", null),
                invitee("<b>Alice</b>"),
                "https://app.example.com/join?token=<unsafe>");

        assertThat(rendered.html())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&lt;b&gt;Alice&lt;/b&gt;")
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<b>Alice</b>");
    }

    @Test
    void stripsLineBreaksFromSubject() {
        var rendered = renderer.render(
                invitation("Planning\nSession\rTitle", null),
                invitee("Alice"),
                "https://app.example.com/join?token=abc");

        assertThat(rendered.subject())
                .isEqualTo("Invitation: Planning Session Title")
                .doesNotContain("\n")
                .doesNotContain("\r");
    }

    private MeetingInvitationsSentMessage invitation(String title, Instant startTime) {
        return new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                "ABC1234567",
                startTime,
                List.of(invitee("Alice")),
                Map.of(),
                Instant.parse("2026-04-02T09:00:00Z"));
    }

    private MeetingInvitationsSentMessage.InviteeInfo invitee(String displayName) {
        return new MeetingInvitationsSentMessage.InviteeInfo(
                UUID.randomUUID(), "alice@example.com", displayName);
    }
}
