package io.github.smiskinext.shared.infrastructure.web;

/**
 * Contributes an additional message-bundle basename to the shared {@link
 * WebI18nConfiguration#messageSource() MessageSource}.
 *
 * <p>Each service that defines its own localized error text implements this as a bean returning a
 * classpath basename (e.g. {@code classpath:messages/meet}). The shared configuration aggregates
 * all contributors, so a service adds its bundle without the shared module needing to know service
 * names. The common bundle ({@code classpath:messages/common}) is always included.
 */
@FunctionalInterface
public interface MessageBundleContributor {

    /**
     * A classpath basename resolved against locale-suffixed properties files, e.g.
     * {@code classpath:messages/meet} → {@code messages/meet.properties},
     * {@code messages/meet_vi.properties}.
     */
    String basename();
}
