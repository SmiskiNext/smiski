package io.github.smiskinext.meet.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.InviteeEmailReplyReceived;
import io.github.smiskinext.meet.application.command.ApplyEmailInviteeResponseCommand;
import io.github.smiskinext.meet.application.usecase.ApplyEmailInviteeResponseUseCase;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code meet.invitee.email-reply.received} CloudEvents published by the notification
 * service when an inbound email carries a valid iMIP METHOD:REPLY.
 *
 * <p>Decodes the proto-JSON payload into {@link InviteeEmailReplyReceived} and delegates to
 * {@link ApplyEmailInviteeResponseUseCase}. Malformed messages are logged and skipped so the
 * consumer partition is never blocked.
 */
@Component
public class InviteeEmailReplyConsumer {

    private static final Logger log = LoggerFactory.getLogger(InviteeEmailReplyConsumer.class);

    private final ApplyEmailInviteeResponseUseCase useCase;

    public InviteeEmailReplyConsumer(ApplyEmailInviteeResponseUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(
            topics = "meet.invitee.email-reply.received",
            groupId = "${app.meet.kafka.email-reply-consumer-group:meet-email-reply}",
            containerFactory = "tenantKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        InviteeEmailReplyReceived proto;
        try {
            proto = decode(event);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed meet.invitee.email-reply.received event id={}: {}",
                    event.getId(),
                    e.getMessage());
            return;
        }

        useCase.execute(new ApplyEmailInviteeResponseCommand(
                proto.getCalendarUid(), proto.getInviteeEmail(), proto.getStatus()));
    }

    private InviteeEmailReplyReceived decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data: id=" + event.getId());
        }
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        InviteeEmailReplyReceived.Builder builder = InviteeEmailReplyReceived.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    "Malformed proto-JSON in email-reply event: " + e.getMessage(), e);
        }
        return builder.build();
    }
}
