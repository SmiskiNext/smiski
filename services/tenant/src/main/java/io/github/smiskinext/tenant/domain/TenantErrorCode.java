package io.github.smiskinext.tenant.domain;

import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;

public enum TenantErrorCode implements ErrorCode {
    MISSING_TENANT_CONTEXT(ErrorCategory.VALIDATION),
    TENANT_NOT_FOUND(ErrorCategory.NOT_FOUND);

    private final ErrorCategory category;

    TenantErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
