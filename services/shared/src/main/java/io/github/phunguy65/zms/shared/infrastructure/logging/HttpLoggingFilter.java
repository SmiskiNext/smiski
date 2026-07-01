package io.github.phunguy65.zms.shared.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that logs inbound HTTP requests ({@code -->}) and outbound responses ({@code
 * <--}) at INFO level. Sets {@code correlationId} in MDC from the {@code X-Correlation-ID} request
 * header, or generates a UUID v4 if absent. Clears MDC after the response is written.
 *
 * <p>Only active when running in a SERVLET container (not Netty/WebFlux).
 */
@ConditionalOnWebApplication(type = Type.SERVLET)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        long start = System.currentTimeMillis();
        log.info(
                "--> {} {} (from={})",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info(
                    "<-- {} {} {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
