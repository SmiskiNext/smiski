package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.TenantRecord;

/**
 * Outbound port for persisting the local tenant projection.
 *
 * <p>Implementations SHALL upsert the supplied record — inserting a new row if none exists for the
 * given {@code tenantId}, or overwriting the mutable columns otherwise.
 */
public interface TenantRepository {

    void upsert(TenantRecord record);
}
