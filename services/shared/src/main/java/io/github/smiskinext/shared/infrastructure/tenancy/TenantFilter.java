package io.github.smiskinext.shared.infrastructure.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that binds the tenant identifier from a configurable request header into {@link
 * TenantContext} for the duration of the request, then clears it.
 *
 * <p>The tenant identifier is injected per service by the infrastructure API gateway; requests
 * without the header fall back to {@link TenantContext#DEFAULT_TENANT}.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
public class TenantFilter extends OncePerRequestFilter {

    private final String headerName;

    public TenantFilter(String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String tenantId = request.getHeader(headerName);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setCurrentTenant(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
