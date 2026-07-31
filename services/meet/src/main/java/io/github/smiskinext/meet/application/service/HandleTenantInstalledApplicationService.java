package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.HandleTenantInstalledCommand;
import io.github.smiskinext.meet.application.usecase.HandleTenantInstalledUseCase;
import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import io.github.smiskinext.meet.domain.port.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the local tenant projection with {@code status = ACTIVE} when a tenant-installed event
 * is received from the tenant service.
 */
@Service
@Transactional
public class HandleTenantInstalledApplicationService implements HandleTenantInstalledUseCase {

    private final TenantRepository tenantRepository;

    public HandleTenantInstalledApplicationService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void handle(HandleTenantInstalledCommand command) {
        tenantRepository.upsert(new TenantRecord(
                command.tenantId(),
                command.cloudId(),
                TenantStatus.ACTIVE,
                command.updatedAt(),
                null,
                null));
    }
}
