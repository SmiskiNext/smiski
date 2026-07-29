package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsGeneratorTest {

    private final IcsGenerator generator = new IcsGenerator();

    @Test
    void requestUsesCalendarUidAndSequenceAndAttendeePerInvitee() {
        InvitationCalendar calendar = new InvitationCalendar(
                "uid-123",
                4,
                "Sprint Planning",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host User",
                List.of(
                        new InvitationCalendar.Attendee("bob@test.com", "Bob", "NEEDS_ACTION"),
                        new InvitationCalendar.Attendee(
                                "carol@test.com", "Carol", "NEEDS_ACTION")));

        String ics = generator.buildRequest(calendar);

        assertThat(ics).contains("METHOD:REQUEST");
        assertThat(ics).contains("UID:uid-123");
        assertThat(ics).contains("SEQUENCE:4");
        assertThat(ics).contains("SUMMARY:Sprint Planning");
        assertThat(ics).contains("bob@test.com");
        assertThat(ics).contains("carol@test.com");
    }

    @Test
    void replyAcceptedMapsToPartStatAccepted() {
        String ics = generator.buildReply(reply("ACCEPTED"));

        assertThat(ics).contains("METHOD:REPLY");
        assertThat(ics).contains("PARTSTAT=ACCEPTED");
        assertThat(ics).contains("bob@test.com");
    }

    @Test
    void replyDeclinedMapsToPartStatDeclined() {
        String ics = generator.buildReply(reply("DECLINED"));

        assertThat(ics).contains("PARTSTAT=DECLINED");
    }

    @Test
    void replyTentativeMapsToPartStatTentative() {
        String ics = generator.buildReply(reply("TENTATIVE"));

        assertThat(ics).contains("PARTSTAT=TENTATIVE");
    }

    @Test
    void replyUsesSameUidAndSequence() {
        String ics = generator.buildReply(reply("ACCEPTED"));

        assertThat(ics).contains("UID:uid-999");
        assertThat(ics).contains("SEQUENCE:7");
    }

    private static ReplyCalendar reply(String responseStatus) {
        return new ReplyCalendar(
                "uid-999",
                7,
                "Sprint Planning",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                "UTC",
                "host@example.com",
                "Host User",
                "bob@test.com",
                "Bob",
                responseStatus);
    }
}
