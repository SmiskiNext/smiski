package io.github.smiskinext.tenant.application.service;

import io.github.smiskinext.shared.domain.EventPublisher;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.application.command.UninstallTenantCommand;
import io.github.smiskinext.tenant.application.mapper.TenantResultMapper;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.application.usecase.UninstallTenantUseCase;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.port.PurgePolicy;
import io.github.smiskinext.tenant.domain.port.TenantRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UninstallTenantApplicationService implements UninstallTenantUseCase {

    private final TenantRepository tenantRepository;
    private final EventPublisher eventPublisher;
    private final PurgePolicy purgePolicy;

    public UninstallTenantApplicationService(
            TenantRepository tenantRepository,
            EventPublisher eventPublisher,
            PurgePolicy purgePolicy) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
        this.purgePolicy = purgePolicy;
    }

    @Override
    @Transactional
    public Result<UninstallTenantResult, TenantError> execute(UninstallTenantCommand command) {
        String cloudId = command.cloudId();
        if (TenantContext.DEFAULT_TENANT.equals(cloudId)) {
            return Result.failure(new TenantError.MissingTenantContext());
        }

        Optional<Tenant> existing = tenantRepository.findById(cloudId);
        if (existing.isEmpty()) {
            return Result.failure(new TenantError.TenantNotFound(cloudId));
        }

        Tenant tenant = existing.get();
        if (tenant.isUninstalled()) {
            return Result.success(TenantResultMapper.toUninstallResult(tenant));
        }

        Instant purgeAfter = Instant.now().plus(purgePolicy.retention());
        tenant.uninstall(purgeAfter);
        tenantRepository.save(tenant);

        eventPublisher.publishEventsOf(tenant);

        return Result.success(TenantResultMapper.toUninstallResult(tenant));
    }
}
