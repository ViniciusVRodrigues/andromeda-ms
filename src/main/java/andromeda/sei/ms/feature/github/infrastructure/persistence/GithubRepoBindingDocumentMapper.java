package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import org.springframework.stereotype.Component;

@Component
public class GithubRepoBindingDocumentMapper {

    public GithubRepoBinding toDomain(GithubRepoBindingDocument doc) {
        if (doc == null) return null;
        return new GithubRepoBinding(
                doc.getId(), doc.getOrganizationId(), doc.getProjectId(), doc.getRepoId(),
                doc.isSyncEnabled(), doc.getLastSyncAt(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public GithubRepoBindingDocument toDocument(GithubRepoBinding binding) {
        if (binding == null) return null;
        return GithubRepoBindingDocument.builder()
                .id(binding.getId())
                .organizationId(binding.getOrganizationId())
                .projectId(binding.getProjectId())
                .repoId(binding.getRepoId())
                .syncEnabled(binding.isSyncEnabled())
                .lastSyncAt(binding.getLastSyncAt())
                .active(binding.isActive())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .version(binding.getVersion())
                .build();
    }
}
