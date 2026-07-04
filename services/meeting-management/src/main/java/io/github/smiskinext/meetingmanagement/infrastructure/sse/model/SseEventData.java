package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base sealed interface for all SSE event payloads.
 *
 * <p>Jackson serializes these to JSON with a discriminator field {@code type}
 * to distinguish between event types on the client side.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JoinRequestCreatedData.class, name = "join_request_created"),
    @JsonSubTypes.Type(value = JoinRequestApprovedData.class, name = "join_request_approved"),
    @JsonSubTypes.Type(value = JoinRequestDeniedData.class, name = "join_request_denied"),
    @JsonSubTypes.Type(value = JoinRequestExpiredData.class, name = "join_request_expired"),
    @JsonSubTypes.Type(value = ParticipantKickedData.class, name = "participant_kicked")
})
public sealed interface SseEventData
        permits JoinRequestCreatedData,
                JoinRequestApprovedData,
                JoinRequestDeniedData,
                JoinRequestExpiredData,
                ParticipantKickedData {}
