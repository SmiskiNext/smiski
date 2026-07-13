package io.github.smiskinext.tenant.domain;

import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCode;

public sealed interface TenantError extends DomainError {

    record MissingTenantContext() implements TenantError {
        @Override
        public ErrorCode errorCode() {
            return TenantErrorCode.MISSING_TENANT_CONTEXT;
        }
    }
}
