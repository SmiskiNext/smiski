package io.github.smiskinext.meet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingTimeZone;
import org.junit.jupiter.api.Test;

class MeetingTimeZoneTest {

    @Test
    void acceptsValidIanaRegionId() {
        MeetingTimeZone zone = MeetingTimeZone.of("Asia/Ho_Chi_Minh");
        assertThat(zone.value()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void rejectsUnknownZoneId() {
        assertThatThrownBy(() -> MeetingTimeZone.of("Mars/Phobos"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown IANA time zone id");
    }

    @Test
    void rejectsBareUtcOffset() {
        assertThatThrownBy(() -> MeetingTimeZone.of("+07:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown IANA time zone id");
    }
}
