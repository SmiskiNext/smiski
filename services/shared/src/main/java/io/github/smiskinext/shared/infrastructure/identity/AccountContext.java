package io.github.smiskinext.shared.infrastructure.identity;

import java.util.Optional;

/**
 * ThreadLocal holder for the account identifier associated with the current request.
 *
 * <p>The account identifier is bound by {@link AccountFilter} from a configurable request header
 * (default {@code X-Account-Id}). The context is cleared per request to avoid leakage across
 * pooled carrier threads (virtual threads enabled).
 */
public final class AccountContext {

    private static final ThreadLocal<String> CURRENT_ACCOUNT = new ThreadLocal<>();

    private AccountContext() {}

    /**
     * Binds the given account identifier to the current thread.
     *
     * @param accountId the account identifier
     */
    public static void setCurrentAccount(String accountId) {
        if (accountId == null) {
            CURRENT_ACCOUNT.remove();
        } else {
            CURRENT_ACCOUNT.set(accountId);
        }
    }

    /**
     * Returns the account identifier bound to the current thread, or empty when none is bound.
     *
     * @return the current account identifier
     */
    public static Optional<String> getCurrentAccount() {
        return Optional.ofNullable(CURRENT_ACCOUNT.get());
    }

    /**
     * Removes the account identifier bound to the current thread.
     */
    public static void clear() {
        CURRENT_ACCOUNT.remove();
    }
}
