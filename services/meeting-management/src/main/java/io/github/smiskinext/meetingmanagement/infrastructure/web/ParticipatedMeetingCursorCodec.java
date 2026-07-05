package io.github.smiskinext.meetingmanagement.infrastructure.web;

import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.shared.domain.CursorErrorCode;
import io.github.smiskinext.shared.domain.CursorTokenEncoder;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.domain.ScrollCursor;
import org.springframework.stereotype.Component;

@Component
public class ParticipatedMeetingCursorCodec {

    private final CursorTokenEncoder cursorTokenEncoder;

    public ParticipatedMeetingCursorCodec(CursorTokenEncoder cursorTokenEncoder) {
        this.cursorTokenEncoder = cursorTokenEncoder;
    }

    public String encode(ParticipatedMeetingCursor cursor) {
        return cursorTokenEncoder.encode(cursor.lastJoinedAt(), cursor.meetingId());
    }

    public Result<ParticipatedMeetingCursor, CursorErrorCode> decode(String token) {
        return switch (cursorTokenEncoder.decode(token)) {
            case Result.Success<ScrollCursor, CursorErrorCode> s ->
                Result.success(new ParticipatedMeetingCursor(
                        s.value().createdAt(), s.value().id()));
            case Result.Failure<ScrollCursor, CursorErrorCode> f -> Result.failure(f.error());
        };
    }
}
