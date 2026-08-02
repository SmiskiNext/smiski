package io.github.smiskinext.notification.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA entity for the local {@code tenants} projection table in the notification service.
 *
 * <p>Keyed by {@code tenant_id} (Jira cloudId). Not partitioned — it is a lookup table with one
 * row per tenant.
 */
@Entity
@Table(name = "tenants")
public class TenantProjectionJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "cloud_id", nullable = false, length = 255)
    private String cloudId;

    @Column(name = "site_url", length = 512)
    private @Nullable String siteUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "uninstalled_at")
    private @Nullable Instant uninstalledAt;

    @Column(name = "purge_after")
    private @Nullable Instant purgeAfter;

    protected TenantProjectionJpaEntity() {}

    public TenantProjectionJpaEntity(
            String tenantId,
            String cloudId,
            @Nullable String siteUrl,
            String status,
            Instant updatedAt,
            @Nullable Instant uninstalledAt,
            @Nullable Instant purgeAfter) {
        this.tenantId = tenantId;
        this.cloudId = cloudId;
        this.siteUrl = siteUrl;
        this.status = status;
        this.updatedAt = updatedAt;
        this.uninstalledAt = uninstalledAt;
        this.purgeAfter = purgeAfter;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public @Nullable String getSiteUrl() {
        return siteUrl;
    }

    public String getStatus() {
        return status;
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

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public void setSiteUrl(@Nullable String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setUninstalledAt(@Nullable Instant uninstalledAt) {
        this.uninstalledAt = uninstalledAt;
    }

    public void setPurgeAfter(@Nullable Instant purgeAfter) {
        this.purgeAfter = purgeAfter;
    }
}
