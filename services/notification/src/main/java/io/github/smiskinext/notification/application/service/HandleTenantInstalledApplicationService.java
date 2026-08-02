package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantInstalledUseCase;
import io.github.smiskinext.notification.domain.model.TenantProjection;
import io.github.smiskinext.notification.domain.model.TenantStatus;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the local tenant projection with {@code status = ACTIVE} when a tenant-installed event
 * is received from the tenant service.
 */
@Service
@Transactional
public class HandleTenantInstalledApplicationService implements HandleTenantInstalledUseCase {

    private final TenantProjectionRepository tenantProjectionRepository;

    public HandleTenantInstalledApplicationService(
            TenantProjectionRepository tenantProjectionRepository) {
        this.tenantProjectionRepository = tenantProjectionRepository;
    }

    @Override
    public void handle(HandleTenantInstalledCommand command) {
        tenantProjectionRepository.upsert(new TenantProjection(
                command.tenantId(),
                command.cloudId(),
                command.siteUrl(),
                TenantStatus.ACTIVE,
                command.updatedAt(),
                null,
                null));
    }
}
