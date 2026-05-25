package andromeda.sei.ms.shared.infrastructure.security;

public final class TenantContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(String organizationId) {
        CONTEXT.set(organizationId);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
