package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.valueobject.JiraIssueLink;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeRange;

public record MeetingInfoSnapshot(
        String title,
        String description,
        JiraIssueLink issueLink,
        String zoneId,
        MeetingTimeRange timeRange) {}
