package andromeda.sei.ms.shared.tenant;

import java.util.UUID;

public class TenantContextHolder {

    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(UUID organizationId) {
        CONTEXT.set(organizationId);
    }

    public static UUID get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
