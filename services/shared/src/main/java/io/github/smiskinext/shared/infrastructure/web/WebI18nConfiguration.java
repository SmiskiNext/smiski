package io.github.smiskinext.shared.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Internationalization for HTTP responses across all servlet services.
 *
 * <p>Wires three cooperating pieces so that error {@code title}/{@code detail} text and Bean
 * Validation messages are localized from the request {@code Accept-Language} header:
 *
 * <ul>
 *   <li>a {@link MessageSource} backed by {@code classpath:messages} bundles;
 *   <li>an {@link AcceptHeaderLocaleResolver} restricted to the supported locales (English default,
 *       Vietnamese);
 *   <li>a {@link LocalValidatorFactoryBean} whose interpolation messages come from the same
 *       {@link MessageSource}, so {@code @Valid} failures honor the request locale.
 * </ul>
 *
 * <p>Declared as a component-scanned {@link Configuration} (not an auto-configuration) so its
 * {@code messageSource} bean takes precedence over Spring Boot's default and is available to every
 * service that scans {@code io.github.smiskinext.shared}.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(ProblemProperties.class)
public class WebI18nConfiguration {

    private static final String COMMON_BUNDLE = "classpath:messages/common";

    private static final List<Locale> SUPPORTED_LOCALES =
            List.of(Locale.ENGLISH, Locale.forLanguageTag("vi"));

    @Bean
    public MessageSource messageSource(ObjectProvider<MessageBundleContributor> contributors) {
        List<String> basenames = new ArrayList<>();
        basenames.add(COMMON_BUNDLE);
        contributors
                .orderedStream()
                .map(MessageBundleContributor::basename)
                .forEach(basenames::add);

        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(basenames.toArray(String[]::new));
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
