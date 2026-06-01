package andromeda.sei.ms.shared.util;

public final class SlugUtils {

    private SlugUtils() {}

    public static String toSlug(String name) {
        return name
                .toLowerCase()
                .replace("&", "and")
                .replace("/", "-")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }
}
