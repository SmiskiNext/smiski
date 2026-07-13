package io.github.smiskinext.tenant.domain.port;

import io.github.smiskinext.tenant.domain.model.Tenant;

import java.util.Optional;

public interface TenantRepository {

    Optional<Tenant> findById(String cloudId);

    Tenant save(Tenant tenant);
}
