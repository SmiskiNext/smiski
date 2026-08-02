package io.github.smiskinext.notification.application.usecase;

import io.github.smiskinext.notification.application.command.HandleTenantInstalledCommand;

/**
 * Inbound port for handling a tenant-installed event.
 */
public interface HandleTenantInstalledUseCase {

    void handle(HandleTenantInstalledCommand command);
}
