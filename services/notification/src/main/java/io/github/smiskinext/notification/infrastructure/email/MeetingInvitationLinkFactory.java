package io.github.smiskinext.notification.infrastructure.email;

import io.github.smiskinext.notification.infrastructure.config.NotificationProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds join links for invite emails using per-invitee invite tokens.
 *
 * <p>Token-based links take the form {@code {joinBaseUrl}?token={inviteToken}} and are the only
 * supported format as of Phase 3. The legacy short-code + password URL format has been removed.
 */
@Component
public class MeetingInvitationLinkFactory {

    private final NotificationProperties notificationProperties;

    public MeetingInvitationLinkFactory(NotificationProperties notificationProperties) {
        this.notificationProperties = notificationProperties;
    }

    /**
     * Builds a token-based invite join link.
     *
     * @param inviteToken the raw per-invitee invite token string
     * @return URL of the form {@code {joinBaseUrl}?token={inviteToken}}
     */
    public String buildInviteLink(String inviteToken) {
        return UriComponentsBuilder.fromUriString(
                        notificationProperties.getInvitation().getJoinBaseUrl())
                .queryParam("token", inviteToken)
                .build()
                .encode()
                .toUriString();
    }
}
