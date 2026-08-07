package io.github.smiskinext.meet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeetingRepositoryAdapterTest {

    private MeetingJpaRepository jpaRepository;
    private MeetingRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(MeetingJpaRepository.class);
        adapter = new MeetingRepositoryAdapter(jpaRepository);
    }

    @Test
    void findActiveByIdDelegatesToJpaRepositoryWithoutLock() {
        UUID meetingId = UUID.randomUUID();
        when(jpaRepository.findActiveById(meetingId)).thenReturn(Optional.empty());

        Optional<?> result = adapter.findActiveById(meetingId);

        assertThat(result).isEmpty();
        verify(jpaRepository).findActiveById(meetingId);
    }

    @Test
    void findActiveByIdReturnsEmptyWhenJpaRepositoryReturnsEmpty() {
        UUID meetingId = UUID.randomUUID();
        when(jpaRepository.findActiveById(meetingId)).thenReturn(Optional.empty());

        Optional<?> result = adapter.findActiveById(meetingId);

        assertThat(result).isEmpty();
    }
}
