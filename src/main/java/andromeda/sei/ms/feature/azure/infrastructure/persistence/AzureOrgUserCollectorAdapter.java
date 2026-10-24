package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.infrastructure.vcs.AzureIdentityIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Upsert by (organizationId, azureId); every read and write is scoped to the given organization. */
@Component
@RequiredArgsConstructor
public class AzureOrgUserCollectorAdapter implements AzureOrgUserCollector {

    /** Team groups carry {@code [Project]\Team} as unique name. */
    private static final Pattern GROUP_NAME = Pattern.compile("^\\[[^\\]]*\\]\\\\");

    private final AzureOrgUserRepository repository;

    @Override
    public void collect(String organizationId, Collection<ObservedIdentity> identities) {
        if (organizationId == null || identities == null || identities.isEmpty()) return;

        Map<String, AzureOrgUser> byAzureId = new LinkedHashMap<>();
        for (AzureOrgUser existing : repository.findAllByOrganizationIdAndActiveTrue(organizationId)) {
            byAzureId.put(existing.getAzureId(), existing);
        }
        Map<String, AzureOrgUser> touched = new LinkedHashMap<>();
        Instant now = Instant.now();
        for (ObservedIdentity seen : identities) {
            String id = AzureIdentityIds.normalize(seen.azureId());
            if (id == null || isGroup(seen.uniqueName())) continue;
            AzureOrgUser user = byAzureId.computeIfAbsent(id, k ->
                    new AzureOrgUser(null, organizationId, k, null, null, null, null, true, now, now, null));
            String email = seen.uniqueName() != null && seen.uniqueName().contains("@") ? seen.uniqueName() : null;
            user.mergeFrom(seen.displayName(), email, seen.uniqueName(), seen.source());
            touched.put(id, user);
        }
        if (!touched.isEmpty()) repository.saveAll(List.copyOf(touched.values()));
    }

    private static boolean isGroup(String uniqueName) {
        return uniqueName != null && GROUP_NAME.matcher(uniqueName).find();
    }
}
