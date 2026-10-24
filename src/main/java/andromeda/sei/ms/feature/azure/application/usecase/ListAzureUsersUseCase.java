package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureUserOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists the persisted org directory (no Azure call): active {@code AzureOrgUser} rows of the caller's
 * organization, deduplicated by azureId, sorted by displayName ignoring case. Groups are never
 * persisted there, so they never appear.
 */
@Component
@RequiredArgsConstructor
public class ListAzureUsersUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final UserRepository userRepository;

    public List<AzureUserOutput> execute() {
        String orgId = TenantContextHolder.get();

        azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        Map<String, AzureOrgUser> byAzureId = new LinkedHashMap<>();
        for (AzureOrgUser identity : azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId)) {
            if (identity.getAzureId() != null) byAzureId.putIfAbsent(identity.getAzureId(), identity);
        }

        Map<String, String> linkedUserByAzureId = new HashMap<>();
        for (User user : userRepository.findAllByOrganizationId(orgId)) {
            if (user.getAzureId() != null) linkedUserByAzureId.putIfAbsent(user.getAzureId(), user.getId());
        }

        return byAzureId.values().stream()
                .sorted(Comparator.comparing((AzureOrgUser i) -> i.getDisplayName() == null ? ""
                        : i.getDisplayName().toLowerCase(Locale.ROOT)))
                .map(i -> new AzureUserOutput(i.getAzureId(), i.getDisplayName(), i.getAzureEmail(),
                        i.getSources().stream().map(Enum::name).sorted().toList(),
                        linkedUserByAzureId.get(i.getAzureId())))
                .toList();
    }
}
