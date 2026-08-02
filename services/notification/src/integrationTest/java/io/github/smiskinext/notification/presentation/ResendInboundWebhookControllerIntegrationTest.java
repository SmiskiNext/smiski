package io.github.smiskinext.notification.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.smiskinext.notification.config.TestcontainersConfiguration;
import io.github.smiskinext.notification.domain.port.InboundEmailReplyProcessor;
import io.github.smiskinext.notification.domain.port.WebhookVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ResendInboundWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookVerifier webhookVerifier;

    @MockitoBean
    private InboundEmailReplyProcessor inboundEmailReplyProcessor;

    @Test
    void validSignatureAcceptsAndProcessesWebhook() throws Exception {
        String payload = """
                {"data": {"email_id": "email-123"}}
                """;

        when(webhookVerifier.verify(eq(payload), any())).thenReturn(true);

        mockMvc.perform(post("/api/1/webhooks/resend/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("svix-id", "msg_test123")
                        .header("svix-timestamp", "1234567890")
                        .header("svix-signature", "v1,validSignature=="))
                .andExpect(status().isOk());

        verify(inboundEmailReplyProcessor).processInboundEmail(payload);
    }

    @Test
    void invalidSignatureReturnsBadRequestWithNoProcessing() throws Exception {
        String payload = """
                {"data": {"email_id": "email-456"}}
                """;

        when(webhookVerifier.verify(eq(payload), any())).thenReturn(false);

        mockMvc.perform(post("/api/1/webhooks/resend/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("svix-id", "msg_test456")
                        .header("svix-timestamp", "1234567890")
                        .header("svix-signature", "v1,invalidSignature=="))
                .andExpect(status().isBadRequest());

        verify(inboundEmailReplyProcessor, never()).processInboundEmail(any());
    }

    @Test
    void missingSignatureHeadersReturnsBadRequestWithNoProcessing() throws Exception {
        String payload = """
                {"data": {"email_id": "email-789"}}
                """;

        when(webhookVerifier.verify(eq(payload), any())).thenReturn(false);

        mockMvc.perform(post("/api/1/webhooks/resend/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(inboundEmailReplyProcessor, never()).processInboundEmail(any());
    }
}
