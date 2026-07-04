package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.notification.infrastructure.messaging.MeetingCancelledMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingCancelledEmailRendererTest {

    private final MeetingCancelledEmailRenderer renderer = new MeetingCancelledEmailRenderer();

    @Test
    void rendersEmailWithRequiredMetadata() {
        var rendered = renderer.render(
                cancellation("Planning Session", Instant.parse("2026-04-03T10:00:00Z")),
                invitee("Alice"));

        assertThat(rendered.subject()).isEqualTo("Meeting Cancelled: Planning Session");
        assertThat(rendered.html())
                .contains("Hello Alice")
                .contains("Planning Session has been cancelled")
                .contains("Fri, 03 Apr 2026 at 10:00 UTC")
                .doesNotContain("Join Meeting");
    }

    @Test
    void usesFallbackCopyForMissingOptionalFields() {
        var rendered = renderer.render(cancellation(null, null), invitee(null));

        assertThat(rendered.subject())
                .isEqualTo("Meeting Cancelled: your Zero Meeting System meeting");
        assertThat(rendered.html())
                .contains("Hello there")
                .contains("your Zero Meeting System meeting")
                .contains("The host had not published a scheduled start time.");
    }

    @Test
    void escapesUserControlledFields() {
        var rendered = renderer.render(
                cancellation("<script>alert(1)</script>", null), invitee("<b>Alice</b>"));

        assertThat(rendered.html())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&lt;b&gt;Alice&lt;/b&gt;")
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<b>Alice</b>");
        assertThat(rendered.subject()).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void stripsControlCharactersFromSubject() {
        var rendered =
                renderer.render(cancellation("Planning\nSession\t\rTitle", null), invitee("Alice"));

        assertThat(rendered.subject())
                .isEqualTo("Meeting Cancelled: Planning Session Title")
                .doesNotContain("\n")
                .doesNotContain("\r")
                .doesNotContain("\t");
    }

    private MeetingCancelledMessage cancellation(String title, Instant startTime) {
        return new MeetingCancelledMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                "ABC1234567",
                startTime,
                List.of(invitee("Alice")),
                Instant.parse("2026-04-02T09:00:00Z"));
    }

    private MeetingCancelledMessage.InviteeInfo invitee(String displayName) {
        return new MeetingCancelledMessage.InviteeInfo(
                UUID.randomUUID(),
                "alice@example.com",
                displayName,
                "ACCEPTED",
                Instant.parse("2026-04-01T09:00:00Z"));
    }
}
