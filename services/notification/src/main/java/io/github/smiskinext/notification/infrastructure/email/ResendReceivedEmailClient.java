package io.github.smiskinext.notification.infrastructure.email;

import com.resend.Resend;
import com.resend.services.receiving.model.AttachmentDetails;
import com.resend.services.receiving.model.ReceivedEmail;
import com.resend.services.receiving.model.ReceivedEmailAttachment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Client for fetching received emails and their text/calendar attachments from the Resend API.
 *
 * <p>Uses the Resend receiving API to retrieve the email metadata and its calendar attachment.
 * Returns null when no calendar attachment is found, allowing the caller to skip non-calendar
 * inbound mail.
 */
@Component
public class ResendReceivedEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendReceivedEmailClient.class);

    private final Resend resend;
    private final HttpClient httpClient;

    public ResendReceivedEmailClient(EmailProperties properties) {
        this.resend = new Resend(properties.getApiKey());
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Fetches the text/calendar attachment content from the received email identified by the
     * given email ID. Returns null when no calendar attachment is found or the fetch fails.
     */
    public @Nullable String fetchCalendarAttachment(String emailId) {
        try {
            ReceivedEmail email = resend.receiving().get(emailId);
            if (email == null) {
                log.debug("Received email not found: {}", emailId);
                return null;
            }

            List<ReceivedEmailAttachment> attachments = email.getAttachments();
            if (attachments == null || attachments.isEmpty()) {
                return null;
            }

            for (ReceivedEmailAttachment attachment : attachments) {
                String contentType = attachment.getContentType();
                if (contentType != null && contentType.toLowerCase().contains("text/calendar")) {
                    return downloadAttachment(emailId, attachment.getId());
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch received email {}: {}", emailId, e.getMessage());
            return null;
        }
    }

    private @Nullable String downloadAttachment(String emailId, String attachmentId) {
        try {
            AttachmentDetails details = resend.receiving().getAttachment(emailId, attachmentId);
            if (details == null || details.getDownloadUrl() == null) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(details.getDownloadUrl()))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.debug(
                    "Attachment download returned status {}: emailId={}, attachmentId={}",
                    response.statusCode(),
                    emailId,
                    attachmentId);
            return null;
        } catch (Exception e) {
            log.warn(
                    "Failed to download attachment emailId={} attachmentId={}: {}",
                    emailId,
                    attachmentId,
                    e.getMessage());
            return null;
        }
    }
}
