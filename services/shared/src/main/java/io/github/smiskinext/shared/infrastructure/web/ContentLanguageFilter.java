package io.github.smiskinext.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the {@code Content-Language} response header to the locale resolved for the request, so
 * clients can tell which language the localized {@code title}/{@code detail} text is in.
 *
 * <p>Runs after Spring resolves the request locale from {@code Accept-Language}, reading the
 * effective locale from {@link LocaleContextHolder}.
 */
@Component
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ContentLanguageFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Locale locale = LocaleContextHolder.getLocale();
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
        filterChain.doFilter(request, response);
    }
}
