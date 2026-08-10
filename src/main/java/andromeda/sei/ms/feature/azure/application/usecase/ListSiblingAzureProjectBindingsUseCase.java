package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListSiblingAzureProjectBindingsUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;

    public List<AzureProjectBinding> execute(String bindingId) {
        String orgId = TenantContextHolder.get();
        return azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue(orgId)
                .stream()
                .filter(b -> !b.getId().equals(bindingId))
                .toList();
    }
}
