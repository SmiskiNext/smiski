package io.github.smiskinext.usermanagement.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.smiskinext.usermanagement.application.helper.UserPreferencesParser;
import io.github.smiskinext.usermanagement.application.query.SearchUsersQuery;
import io.github.smiskinext.usermanagement.domain.port.UserRepository;
import io.github.smiskinext.usermanagement.domain.projection.UserSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SearchUsersUseCaseTest {

    @Mock
    UserRepository userRepository;

    SearchUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        var preferencesParser = new UserPreferencesParser(new ObjectMapper());
        useCase = new SearchUsersUseCase(userRepository, preferencesParser);
    }

    private UserSummary buildUser(String email) {
        Instant now = Instant.now();
        return new UserSummary(
                UuidCreator.getTimeOrderedEpoch(),
                email,
                "User",
                null,
                null,
                "EMAIL",
                null,
                now,
                now);
    }

    @Test
    void execute_firstPage_returnsMappedContent() {
        var users = List.of(buildUser("a@example.com"), buildUser("b@example.com"));
        when(userRepository.searchSummaries(isNull(), anyInt(), any()))
                .thenReturn(CursorPageResponse.of(users, 20, false));

        var result = useCase.execute(new SearchUsersQuery(null, 20, null), null);

        assertThat(result.items()).hasSize(2);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void execute_hasNextPage_flagsHasNext() {
        var users = List.of(buildUser("a@example.com"), buildUser("b@example.com"));
        when(userRepository.searchSummaries(isNull(), anyInt(), any()))
                .thenReturn(CursorPageResponse.of(users, 20, true));

        var result = useCase.execute(new SearchUsersQuery(null, 20, null), null);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void execute_emptyResult_returnsEmptyContent() {
        when(userRepository.searchSummaries(isNull(), anyInt(), any()))
                .thenReturn(CursorPageResponse.empty(20));

        var result = useCase.execute(new SearchUsersQuery(null, 20, null), null);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }
}
