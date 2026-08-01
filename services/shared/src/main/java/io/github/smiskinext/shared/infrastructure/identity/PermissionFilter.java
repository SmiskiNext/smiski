package io.github.smiskinext.shared.infrastructure.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that reads the project permission header, parses it as a comma-separated list of
 * permission keys, and binds the result to both {@link PermissionContext} and the Spring Security
 * {@link SecurityContextHolder} for the duration of the request, then clears both.
 *
 * <p>Each permission key is registered as a {@link SimpleGrantedAuthority}, enabling
 * {@code @PreAuthorize("hasAuthority('...')")} checks on controller methods.
 *
 * <p>When the header is absent and {@code requireHeader} is {@code false}, an empty permission set
 * is bound and the request proceeds. When the header is absent and {@code requireHeader} is
 * {@code true}, the filter rejects the request with {@code 403 application/problem+json}.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
public class PermissionFilter extends OncePerRequestFilter {

    private static final String FORBIDDEN_BODY =
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Missing required project permissions header\","
                    + "\"code\":\"NOT_AUTHORIZED\"}";

    private final String headerName;
    private final boolean requireHeader;

    public PermissionFilter(String headerName, boolean requireHeader) {
        this.headerName = headerName;
        this.requireHeader = requireHeader;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String headerValue = request.getHeader(headerName);

        try {
            if (headerValue != null && !headerValue.isBlank()) {
                Set<String> permissions = Arrays.stream(headerValue.split(","))
                        .map(String::trim)
                        .filter(token -> !token.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                PermissionContext.setPermissions(permissions);
                bindSecurityContext(permissions);
                filterChain.doFilter(request, response);
            } else if (requireHeader) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.getWriter().write(FORBIDDEN_BODY);
            } else {
                PermissionContext.setPermissions(new LinkedHashSet<>());
                bindSecurityContext(Set.of());
                filterChain.doFilter(request, response);
            }
        } finally {
            PermissionContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static void bindSecurityContext(Set<String> permissions) {
        var authorities =
                permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        var authentication = new PreAuthenticatedAuthenticationToken("gateway", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
