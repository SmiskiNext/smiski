package io.github.smiskinext.shared.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountFilterTest {

    private final AccountFilter filter = new AccountFilter("X-Account-Id");

    @AfterEach
    void cleanup() {
        AccountContext.clear();
    }

    @Test
    void bindsAccountFromHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Account-Id", "acc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<String>> captured = new AtomicReference<>();

        filter.doFilterInternal(request, response, (req, res) -> {
            captured.set(AccountContext.getCurrentAccount());
        });

        assertThat(captured.get()).contains("acc-123");
    }

    @Test
    void clearsContextAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Account-Id", "acc-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertThat(AccountContext.getCurrentAccount()).isEmpty();
    }

    @Test
    void clearsContextEvenOnException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Account-Id", "acc-789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("downstream failure");
        };

        try {
            filter.doFilterInternal(request, response, failingChain);
        } catch (ServletException ignored) {
        }

        assertThat(AccountContext.getCurrentAccount()).isEmpty();
    }

    @Test
    void noHeaderMeansEmptyContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<String>> captured = new AtomicReference<>();

        filter.doFilterInternal(request, response, (req, res) -> {
            captured.set(AccountContext.getCurrentAccount());
        });

        assertThat(captured.get()).isEmpty();
    }

    @Test
    void noLeakAcrossRequests() throws ServletException, IOException {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.addHeader("X-Account-Id", "acc-first");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(firstRequest, response, (req, res) -> {});

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        AtomicReference<Optional<String>> captured = new AtomicReference<>();
        filter.doFilterInternal(secondRequest, response, (req, res) -> {
            captured.set(AccountContext.getCurrentAccount());
        });

        assertThat(captured.get()).isEmpty();
    }
}
