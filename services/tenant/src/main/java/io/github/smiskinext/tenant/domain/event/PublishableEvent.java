package io.github.smiskinext.tenant.domain.event;

/**
 * Tenant-specific marker extending the shared {@link io.github.smiskinext.shared.domain.PublishableEvent}.
 *
 * <p>Unlike the meet service's aggregate identity (UUID), the tenant service uses String (cloudId)
 * as its aggregate identity.
 */
public interface PublishableEvent extends io.github.smiskinext.shared.domain.PublishableEvent {}
