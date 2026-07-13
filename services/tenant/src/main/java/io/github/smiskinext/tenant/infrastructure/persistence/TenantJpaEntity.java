package io.github.smiskinext.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tenants")
public class TenantJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "installation_id", nullable = false, length = 255, unique = true)
    private String installationId;

    @Column(name = "app_id", nullable = false, length = 255)
    private String appId;

    @Column(name = "environment_type", nullable = false, length = 20)
    private String environmentType;

    @Column(name = "environment_id", length = 255)
    private @Nullable String environmentId;

    @Column(name = "site_url", length = 512)
    private @Nullable String siteUrl;

    @Column(name = "installer_account_id", length = 128)
    private @Nullable String installerAccountId;

    @Column(name = "app_version", length = 50)
    private @Nullable String appVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "uninstalled_at")
    private @Nullable Instant uninstalledAt;

    @Column(name = "purge_after")
    private @Nullable Instant purgeAfter;

    protected TenantJpaEntity() {}

    public TenantJpaEntity(
            String tenantId,
            String installationId,
            String appId,
            String environmentType,
            @Nullable String environmentId,
            @Nullable String siteUrl,
            @Nullable String installerAccountId,
            @Nullable String appVersion,
            String status,
            Instant installedAt,
            Instant updatedAt,
            @Nullable Instant uninstalledAt,
            @Nullable Instant purgeAfter) {
        this.tenantId = tenantId;
        this.installationId = installationId;
        this.appId = appId;
        this.environmentType = environmentType;
        this.environmentId = environmentId;
        this.siteUrl = siteUrl;
        this.installerAccountId = installerAccountId;
        this.appVersion = appVersion;
        this.status = status;
        this.installedAt = installedAt;
        this.updatedAt = updatedAt;
        this.uninstalledAt = uninstalledAt;
        this.purgeAfter = purgeAfter;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getInstallationId() {
        return installationId;
    }

    public void setInstallationId(String installationId) {
        this.installationId = installationId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getEnvironmentType() {
        return environmentType;
    }

    public void setEnvironmentType(String environmentType) {
        this.environmentType = environmentType;
    }

    public @Nullable String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(@Nullable String environmentId) {
        this.environmentId = environmentId;
    }

    public @Nullable String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(@Nullable String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public @Nullable String getInstallerAccountId() {
        return installerAccountId;
    }

    public void setInstallerAccountId(@Nullable String installerAccountId) {
        this.installerAccountId = installerAccountId;
    }

    public @Nullable String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(@Nullable String appVersion) {
        this.appVersion = appVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public @Nullable Instant getUninstalledAt() {
        return uninstalledAt;
    }

    public void setUninstalledAt(@Nullable Instant uninstalledAt) {
        this.uninstalledAt = uninstalledAt;
    }

    public @Nullable Instant getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(@Nullable Instant purgeAfter) {
        this.purgeAfter = purgeAfter;
    }
}
