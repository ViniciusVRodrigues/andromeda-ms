package andromeda.sei.ms.feature.organization.domain;

import java.time.Instant;
import java.util.UUID;

public record Organization(UUID id, String name, String slug, Instant createdAt) {

    public static Organization create(String name) {
        return new Organization(null, name, generateSlug(name), null);
    }

    private static String generateSlug(String name) {
        return name.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
