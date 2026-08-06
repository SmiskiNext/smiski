package io.github.smiskinext.shared.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PermissionFilterTest {

    @AfterEach
    void cleanup() {
        PermissionContext.clear();
    }

    @Test
    void headerPresentBindsPermissionsToContext() throws ServletException, IOException {
        PermissionFilter filter = new PermissionFilter("X-Project-Permissions", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Project-Permissions", "view-meeting,edit-meeting");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> hasViewMeeting = new AtomicReference<>();

        filter.doFilterInternal(request, response, (req, res) -> {
            hasViewMeeting.set(PermissionContext.hasPermission("view-meeting"));
        });

        assertThat(hasViewMeeting.get()).isTrue();
    }

    @Test
    void headerAbsentWithRequireHeaderFalsePassesThroughWithEmptyContext()
            throws ServletException, IOException {
        PermissionFilter filter = new PermissionFilter("X-Project-Permissions", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> filterChainCalled = new AtomicReference<>(false);

        filter.doFilterInternal(request, response, (req, res) -> {
            filterChainCalled.set(true);
        });

        assertThat(filterChainCalled.get()).isTrue();
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void headerAbsentWithRequireHeaderTrueReturnsForbidden() throws ServletException, IOException {
        PermissionFilter filter = new PermissionFilter("X-Project-Permissions", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> filterChainCalled = new AtomicReference<>(false);

        filter.doFilterInternal(request, response, (req, res) -> {
            filterChainCalled.set(true);
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(filterChainCalled.get()).isFalse();
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("NOT_AUTHORIZED");
    }

    @Test
    void contextClearedAfterRequestPreventsLeakToNextRequest()
            throws ServletException, IOException {
        PermissionFilter filter = new PermissionFilter("X-Project-Permissions", false);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.addHeader("X-Project-Permissions", "edit-meeting");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(firstRequest, response, (req, res) -> {});

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        AtomicReference<Boolean> hasEditMeeting = new AtomicReference<>();
        filter.doFilterInternal(secondRequest, response, (req, res) -> {
            hasEditMeeting.set(PermissionContext.hasPermission("edit-meeting"));
        });

        assertThat(hasEditMeeting.get()).isFalse();
    }
}
