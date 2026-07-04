package io.github.smiskinext.usermanagement.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code x-user-id} metadata key forwarded by {@code meeting-management}'s
 * {@code AuthMetadataInterceptor} via gRPC HTTP/2 headers and populates the Spring
 * {@link SecurityContextHolder}.
 *
 * <p>In servlet mode, Spring gRPC maps gRPC metadata to HTTP headers, so this filter
 * works identically to a regular HTTP header filter. The {@code JwtAuthFilter} runs
 * first; if it already set an {@code Authentication}, this filter is a no-op.
 */
@Component
public class GrpcUserIdFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "x-user-id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
