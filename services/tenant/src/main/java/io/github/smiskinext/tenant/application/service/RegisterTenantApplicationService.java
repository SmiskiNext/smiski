package io.github.smiskinext.tenant.application.service;

import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.mapper.TenantResponseMapper;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.application.usecase.RegisterTenantUseCase;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.domain.event.PublishableEvent;
import io.github.smiskinext.tenant.domain.model.Tenant;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import io.github.smiskinext.tenant.domain.port.EventPublisher;
import io.github.smiskinext.tenant.domain.port.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterTenantApplicationService implements RegisterTenantUseCase {

    private final TenantRepository tenantRepository;
    private final EventPublisher eventPublisher;

    public RegisterTenantApplicationService(
            TenantRepository tenantRepository, EventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<TenantResponse, TenantError> execute(RegisterTenantCommand command) {
        String cloudId = command.cloudId();
        if (TenantContext.DEFAULT_TENANT.equals(cloudId)) {
            return Result.failure(new TenantError.MissingTenantContext());
        }

        Optional<Tenant> existing = tenantRepository.findById(cloudId);
        boolean created = existing.isEmpty();

        Tenant tenant;
        if (created) {
            tenant = Tenant.install(
                    cloudId,
                    new InstallationId(command.installationId()),
                    new AppId(command.appId()),
                    command.appVersion(),
                    command.environmentId(),
                    command.siteUrl(),
                    command.installerAccountId());
        } else {
            tenant = existing.get();
            tenant.reinstall(
                    new InstallationId(command.installationId()),
                    new AppId(command.appId()),
                    command.appVersion(),
                    command.environmentId(),
                    command.siteUrl(),
                    command.installerAccountId());
        }

        tenantRepository.save(tenant);

        for (DomainEvent event : tenant.getDomainEvents()) {
            if (event instanceof PublishableEvent publishable) {
                eventPublisher.publish(publishable);
            }
        }
        tenant.clearDomainEvents();

        return Result.success(TenantResponseMapper.toResponse(tenant, created));
    }
}
