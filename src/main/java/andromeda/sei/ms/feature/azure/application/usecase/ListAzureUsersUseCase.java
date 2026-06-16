package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureUserOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamMemberDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListAzureUsersUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureAuthStrategyResolver authStrategyResolver;

    public List<AzureUserOutput> execute() {
        String orgId = TenantContextHolder.get();

        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId);

        if (bindings.isEmpty()) {
            return List.of();
        }

        String authHeader = authStrategyResolver.resolve(connection);
        String orgUrl = connection.getAzureOrgUrl();

        Map<String, AzureUserOutput> byAzureId = new LinkedHashMap<>();
        for (AzureProjectBinding binding : bindings) {
            List<AdoTeamDto> teams = azureDevOpsClient.getTeams(orgUrl, binding.getAzureProjectId(), authHeader);
            for (AdoTeamDto team : teams) {
                List<AdoTeamMemberDto> members = azureDevOpsClient.getTeamMembers(orgUrl, binding.getAzureProjectId(), team.id(), authHeader);
                for (AdoTeamMemberDto member : members) {
                    if (member.id() != null) {
                        byAzureId.putIfAbsent(member.id(),
                                new AzureUserOutput(member.id(), member.displayName(), member.uniqueName()));
                    }
                }
            }
        }

        return new ArrayList<>(byAzureId.values()).stream()
                .sorted(Comparator.comparing(u -> u.displayName() != null ? u.displayName() : ""))
                .toList();
    }
}
