package io.github.smiskinext.shared.infrastructure.identity;

import java.util.Collections;
import java.util.Set;

/**
 * ThreadLocal holder for the project permission set associated with the current request.
 *
 * <p>Permissions are bound by {@link PermissionFilter} from a configurable request header
 * (default {@code X-Project-Permissions}). The context is cleared per request to avoid leakage
 * across pooled carrier threads (virtual threads enabled).
 */
public final class PermissionContext {

    private static final ThreadLocal<Set<String>> PERMISSIONS = new ThreadLocal<>();

    private PermissionContext() {}

    /**
     * Binds the given permission set to the current thread.
     *
     * @param permissions the permission keys; {@code null} removes any bound value
     */
    public static void setPermissions(Set<String> permissions) {
        if (permissions == null) {
            PERMISSIONS.remove();
        } else {
            PERMISSIONS.set(Collections.unmodifiableSet(permissions));
        }
    }

    /**
     * Returns the permission set bound to the current thread.
     *
     * @return the current permission set; never {@code null}, empty when none is bound
     */
    public static Set<String> getPermissions() {
        Set<String> perms = PERMISSIONS.get();
        return perms != null ? perms : Collections.emptySet();
    }

    /**
     * Returns {@code true} when the current thread's permission set contains the given key.
     *
     * @param key the permission key to test
     * @return {@code true} if the permission is present
     */
    public static boolean hasPermission(String key) {
        return getPermissions().contains(key);
    }

    /**
     * Removes the permission set bound to the current thread.
     */
    public static void clear() {
        PERMISSIONS.remove();
    }
}
