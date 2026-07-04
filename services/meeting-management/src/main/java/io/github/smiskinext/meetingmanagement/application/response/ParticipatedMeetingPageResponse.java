package io.github.smiskinext.meetingmanagement.application.response;

import java.util.List;

public record ParticipatedMeetingPageResponse(
        List<ParticipatedMeetingListItemResponse> items, int pageSize, boolean hasNext) {}
