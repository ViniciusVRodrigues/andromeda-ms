package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepo;
import org.springframework.stereotype.Component;

@Component
public class GithubRepoDocumentMapper {

    public GithubRepo toDomain(GithubRepoDocument doc) {
        if (doc == null) return null;
        return new GithubRepo(
                doc.getId(), doc.getOrganizationId(), doc.getExternalId(), doc.getOwner(), doc.getName(),
                doc.getFullName(), doc.getExternalUpdatedAt(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public GithubRepoDocument toDocument(GithubRepo repo) {
        if (repo == null) return null;
        return GithubRepoDocument.builder()
                .id(repo.getId())
                .organizationId(repo.getOrganizationId())
                .externalId(repo.getExternalId())
                .owner(repo.getOwner())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .externalUpdatedAt(repo.getExternalUpdatedAt())
                .active(repo.isActive())
                .createdAt(repo.getCreatedAt())
                .updatedAt(repo.getUpdatedAt())
                .version(repo.getVersion())
                .build();
    }
}
