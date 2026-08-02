package io.github.smiskinext.notification.application.service;

import io.github.smiskinext.notification.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.notification.application.usecase.HandleTenantUninstalledUseCase;
import io.github.smiskinext.notification.domain.model.TenantProjection;
import io.github.smiskinext.notification.domain.model.TenantStatus;
import io.github.smiskinext.notification.domain.port.TenantProjectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the local tenant projection with {@code status = UNINSTALLED} when a
 * tenant-uninstalled event is received from the tenant service.
 */
@Service
@Transactional
public class HandleTenantUninstalledApplicationService implements HandleTenantUninstalledUseCase {

    private final TenantProjectionRepository tenantProjectionRepository;

    public HandleTenantUninstalledApplicationService(
            TenantProjectionRepository tenantProjectionRepository) {
        this.tenantProjectionRepository = tenantProjectionRepository;
    }

    @Override
    public void handle(HandleTenantUninstalledCommand command) {
        tenantProjectionRepository.upsert(new TenantProjection(
                command.tenantId(),
                command.cloudId(),
                null,
                TenantStatus.UNINSTALLED,
                command.updatedAt(),
                command.uninstalledAt(),
                command.purgeAfter()));
    }
}
