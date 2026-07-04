package io.github.smiskinext.meetingmanagement.application.response;

import java.time.Instant;

public record ParticipatedMeetingListItemResponse(Instant lastJoinedAt, MeetingResponse meeting) {}
