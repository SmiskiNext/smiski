package io.github.smiskinext.notification.domain.port;

public interface EmailSender {

    void send(String toEmail, String subject, String html);
}
