package andromeda.sei.ms.feature.vcs.application.usecase;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Normalization shared by the service identity settings use cases: trimmed, non-blank, unique. */
final class ServiceIdentitySettings {

    static final int MAX_IDENTITIES = 200;

    private ServiceIdentitySettings() {}

    static List<String> normalize(Collection<String> identities) {
        if (identities == null) return List.of();
        return identities.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(identity -> !identity.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(MAX_IDENTITIES)
                .toList();
    }
}
