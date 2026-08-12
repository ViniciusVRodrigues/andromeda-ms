package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubCommit;
import org.springframework.stereotype.Component;

@Component
public class GithubCommitDocumentMapper {

    public GithubCommit toDomain(GithubCommitDocument doc) {
        if (doc == null) return null;
        return new GithubCommit(
                doc.getId(), doc.getOrganizationId(), doc.getRepoId(), doc.getSha(), doc.getMessage(),
                doc.getAuthorGithubLogin(), doc.getAuthorUserId(), doc.getAuthoredAt(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public GithubCommitDocument toDocument(GithubCommit commit) {
        if (commit == null) return null;
        return GithubCommitDocument.builder()
                .id(commit.getId())
                .organizationId(commit.getOrganizationId())
                .repoId(commit.getRepoId())
                .sha(commit.getSha())
                .message(commit.getMessage())
                .authorGithubLogin(commit.getAuthorGithubLogin())
                .authorUserId(commit.getAuthorUserId())
                .authoredAt(commit.getAuthoredAt())
                .active(commit.isActive())
                .createdAt(commit.getCreatedAt())
                .updatedAt(commit.getUpdatedAt())
                .version(commit.getVersion())
                .build();
    }
}
