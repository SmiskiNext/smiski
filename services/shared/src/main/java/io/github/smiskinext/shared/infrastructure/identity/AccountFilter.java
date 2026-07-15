package io.github.smiskinext.shared.infrastructure.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that binds the account identifier from a configurable request header into
 * {@link AccountContext} for the duration of the request, then clears it.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
public class AccountFilter extends OncePerRequestFilter {

    private final String headerName;

    public AccountFilter(String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accountId = request.getHeader(headerName);
        if (accountId != null && !accountId.isBlank()) {
            AccountContext.setCurrentAccount(accountId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            AccountContext.clear();
        }
    }
}
