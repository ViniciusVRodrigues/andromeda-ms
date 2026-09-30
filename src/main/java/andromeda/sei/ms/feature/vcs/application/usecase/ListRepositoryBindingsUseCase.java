package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Active repository bindings of the organization or of one project, with display names (one batch read). */
@Component
@RequiredArgsConstructor
public class ListRepositoryBindingsUseCase {

    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;

    /** @param projectId {@code null} lists every active binding of the organization */
    public List<RepoBindingOutput> execute(String projectId) {
        String orgId = TenantContextHolder.get();
        List<VcsRepoBinding> bindings = projectId == null
                ? bindingRepository.findAllActiveByOrganizationId(orgId)
                : bindingRepository.findAllActiveByOrganizationIdAndProjectId(orgId, projectId);
        Map<String, VcsRepository> repositories = repositoryRepository
                .findAllByOrganizationIdAndIdIn(orgId, bindings.stream().map(VcsRepoBinding::getRepositoryId).toList())
                .stream()
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
        return bindings.stream()
                .map(binding -> RepoBindingOutputs.of(binding, repositories.get(binding.getRepositoryId())))
                .toList();
    }
}
