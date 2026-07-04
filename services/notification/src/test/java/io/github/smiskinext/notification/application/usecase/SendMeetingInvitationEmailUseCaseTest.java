package io.github.smiskinext.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.MeetingInvitationEmailRenderer;
import io.github.smiskinext.notification.infrastructure.email.MeetingInvitationLinkFactory;
import io.github.smiskinext.notification.infrastructure.messaging.MeetingInvitationsSentMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMeetingInvitationEmailUseCaseTest {

    @Mock
    private MeetingInvitationLinkFactory linkFactory;

    @Mock
    private MeetingInvitationEmailRenderer renderer;

    @Mock
    private EmailSender emailSender;

    private SendMeetingInvitationEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendMeetingInvitationEmailUseCase(linkFactory, renderer, emailSender);
    }

    @Test
    void usesTokenBasedLinkWhenInviteTokenIsAvailable() {
        UUID inviteeRecordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        assertThat(inviteeRecordId).isNotEqualTo(userId);
        MeetingInvitationsSentMessage invitation = new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planning Session",
                "ABC1234567",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingInvitationsSentMessage.InviteeInfo(
                        userId, "alice@example.com", "Alice")),
                Map.of(userId, "raw-invite-token-abc"),
                Instant.parse("2026-04-02T09:00:00Z"));
        MeetingInvitationsSentMessage.InviteeInfo invitee =
                invitation.invitees().getFirst();

        when(linkFactory.buildInviteLink("raw-invite-token-abc"))
                .thenReturn("https://app.example.com/join?token=raw-invite-token-abc");
        when(renderer.render(
                        invitation,
                        invitee,
                        "https://app.example.com/join?token=raw-invite-token-abc"))
                .thenReturn(new MeetingInvitationEmailRenderer.RenderedEmail(
                        "Invitation: Planning Session", "<p>Hello Alice</p>"));

        useCase.send(invitation, invitee);

        verify(linkFactory).buildInviteLink("raw-invite-token-abc");
        verify(emailSender)
                .send("alice@example.com", "Invitation: Planning Session", "<p>Hello Alice</p>");
    }

    @Test
    void throwsWhenNoInviteTokenIsAvailableForInvitee() {
        MeetingInvitationsSentMessage invitation = new MeetingInvitationsSentMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Legacy Meeting",
                "XYZ9998888",
                Instant.parse("2026-04-03T10:00:00Z"),
                List.of(new MeetingInvitationsSentMessage.InviteeInfo(
                        null, "bob@example.com", "Bob")),
                null,
                Instant.parse("2026-04-02T09:00:00Z"));
        MeetingInvitationsSentMessage.InviteeInfo invitee =
                invitation.invitees().getFirst();

        assertThatThrownBy(() -> useCase.send(invitation, invitee))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No invite token found for invitee");
    }
}
