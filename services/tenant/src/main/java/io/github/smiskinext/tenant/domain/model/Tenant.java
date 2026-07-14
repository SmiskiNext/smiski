package io.github.smiskinext.tenant.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.tenant.domain.event.TenantInstalledEvent;
import io.github.smiskinext.tenant.domain.model.valueobject.AppId;
import io.github.smiskinext.tenant.domain.model.valueobject.InstallationId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public class Tenant extends AggregateRoot<String> {

    private final String cloudId;
    private InstallationId installationId;
    private AppId appId;
    private @Nullable String appVersion;
    private @Nullable String environmentId;
    private @Nullable String siteUrl;
    private @Nullable String installerAccountId;
    private TenantStatus status;
    private Instant installedAt;
    private Instant updatedAt;
    private @Nullable Instant uninstalledAt;
    private @Nullable Instant purgeAfter;

    private Tenant(
            String cloudId,
            InstallationId installationId,
            AppId appId,
            @Nullable String appVersion,
            @Nullable String environmentId,
            @Nullable String siteUrl,
            @Nullable String installerAccountId,
            TenantStatus status,
            Instant installedAt,
            Instant updatedAt,
            @Nullable Instant uninstalledAt,
            @Nullable Instant purgeAfter) {
        this.cloudId = cloudId;
        this.installationId = installationId;
        this.appId = appId;
        this.appVersion = appVersion;
        this.environmentId = environmentId;
        this.siteUrl = siteUrl;
        this.installerAccountId = installerAccountId;
        this.status = status;
        this.installedAt = installedAt;
        this.updatedAt = updatedAt;
        this.uninstalledAt = uninstalledAt;
        this.purgeAfter = purgeAfter;
    }

    public static Tenant install(
            String cloudId,
            InstallationId installationId,
            AppId appId,
            @Nullable String appVersion,
            @Nullable String environmentId,
            @Nullable String siteUrl,
            @Nullable String installerAccountId) {
        Instant now = Instant.now();
        var tenant = new Tenant(
                cloudId,
                installationId,
                appId,
                appVersion,
                environmentId,
                siteUrl,
                installerAccountId,
                TenantStatus.ACTIVE,
                now,
                now,
                null,
                null);
        tenant.registerInstalledEvent();
        return tenant;
    }

    public void reinstall(
            InstallationId installationId,
            AppId appId,
            @Nullable String appVersion,
            @Nullable String environmentId,
            @Nullable String siteUrl,
            @Nullable String installerAccountId) {
        this.installationId = installationId;
        this.appId = appId;
        this.appVersion = appVersion;
        this.environmentId = environmentId;
        this.siteUrl = siteUrl;
        this.installerAccountId = installerAccountId;
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = Instant.now();
        registerInstalledEvent();
    }

    public static Tenant reconstitute(
            String cloudId,
            InstallationId installationId,
            AppId appId,
            @Nullable String appVersion,
            @Nullable String environmentId,
            @Nullable String siteUrl,
            @Nullable String installerAccountId,
            TenantStatus status,
            Instant installedAt,
            Instant updatedAt,
            @Nullable Instant uninstalledAt,
            @Nullable Instant purgeAfter) {
        return new Tenant(
                cloudId,
                installationId,
                appId,
                appVersion,
                environmentId,
                siteUrl,
                installerAccountId,
                status,
                installedAt,
                updatedAt,
                uninstalledAt,
                purgeAfter);
    }

    private void registerInstalledEvent() {
        registerEvent(new TenantInstalledEvent(
                UuidCreator.getTimeOrderedEpoch(),
                cloudId,
                installationId.value(),
                appId.value(),
                appVersion,
                environmentId,
                siteUrl,
                installerAccountId,
                installedAt));
    }

    @Override
    public String getId() {
        return cloudId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public InstallationId getInstallationId() {
        return installationId;
    }

    public AppId getAppId() {
        return appId;
    }

    public @Nullable String getAppVersion() {
        return appVersion;
    }

    public @Nullable String getEnvironmentId() {
        return environmentId;
    }

    public @Nullable String getSiteUrl() {
        return siteUrl;
    }

    public @Nullable String getInstallerAccountId() {
        return installerAccountId;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Instant getUninstalledAt() {
        return uninstalledAt;
    }

    public @Nullable Instant getPurgeAfter() {
        return purgeAfter;
    }
}
