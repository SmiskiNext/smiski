package io.github.smiskinext.meet.application.service;

import io.github.smiskinext.meet.application.command.HandleTenantUninstalledCommand;
import io.github.smiskinext.meet.application.usecase.HandleTenantUninstalledUseCase;
import io.github.smiskinext.meet.domain.model.TenantRecord;
import io.github.smiskinext.meet.domain.model.TenantStatus;
import io.github.smiskinext.meet.domain.port.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the local tenant projection with {@code status = UNINSTALLED} and records
 * {@code uninstalledAt} and {@code purgeAfter} when a tenant-uninstalled event is received.
 */
@Service
@Transactional
public class HandleTenantUninstalledApplicationService implements HandleTenantUninstalledUseCase {

    private final TenantRepository tenantRepository;

    public HandleTenantUninstalledApplicationService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void handle(HandleTenantUninstalledCommand command) {
        tenantRepository.upsert(new TenantRecord(
                command.tenantId(),
                command.cloudId(),
                TenantStatus.UNINSTALLED,
                command.updatedAt(),
                command.uninstalledAt(),
                command.purgeAfter()));
    }
}
