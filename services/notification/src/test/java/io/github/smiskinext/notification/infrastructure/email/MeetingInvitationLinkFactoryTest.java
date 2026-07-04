package io.github.smiskinext.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.notification.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeetingInvitationLinkFactoryTest {

    private MeetingInvitationLinkFactory linkFactory;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.getResend().setApiKey("re_test_key");
        properties.getResend().setFromEmail("notifications@example.com");
        properties.getResend().setFromName("Zero Meeting System");
        properties.getInvitation().setJoinBaseUrl("https://app.example.com/join");
        properties.afterPropertiesSet();
        linkFactory = new MeetingInvitationLinkFactory(properties);
    }

    @Test
    void buildsTokenBasedInviteLink() {
        assertThat(linkFactory.buildInviteLink("abc123tokenvalue"))
                .isEqualTo("https://app.example.com/join?token=abc123tokenvalue");
    }

    @Test
    void encodesTokenCharactersInInviteLink() {
        assertThat(linkFactory.buildInviteLink("abc+def/ghi=")).contains("token=");
    }
}
