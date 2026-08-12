package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import org.springframework.stereotype.Component;

@Component
public class GithubPullRequestDocumentMapper {

    public GithubPullRequest toDomain(GithubPullRequestDocument doc) {
        if (doc == null) return null;
        return new GithubPullRequest(
                doc.getId(), doc.getOrganizationId(), doc.getRepoId(), doc.getExternalId(), doc.getNumber(),
                doc.getTitle(), doc.getStatus(), doc.getAuthorGithubLogin(), doc.getAuthorUserId(),
                doc.getCreatedAt(), doc.getMergedAt(), doc.getClosedAt(), doc.getExternalUpdatedAt(),
                doc.getCycleTimeHours(), doc.getTimeToFirstReviewHours(),
                doc.isActive(), doc.getUpdatedAt(), doc.getVersion());
    }

    public GithubPullRequestDocument toDocument(GithubPullRequest pr) {
        if (pr == null) return null;
        return GithubPullRequestDocument.builder()
                .id(pr.getId())
                .organizationId(pr.getOrganizationId())
                .repoId(pr.getRepoId())
                .externalId(pr.getExternalId())
                .number(pr.getNumber())
                .title(pr.getTitle())
                .status(pr.getStatus())
                .authorGithubLogin(pr.getAuthorGithubLogin())
                .authorUserId(pr.getAuthorUserId())
                .createdAt(pr.getCreatedAt())
                .mergedAt(pr.getMergedAt())
                .closedAt(pr.getClosedAt())
                .externalUpdatedAt(pr.getExternalUpdatedAt())
                .cycleTimeHours(pr.getCycleTimeHours())
                .timeToFirstReviewHours(pr.getTimeToFirstReviewHours())
                .active(pr.isActive())
                .updatedAt(pr.getUpdatedAt())
                .version(pr.getVersion())
                .build();
    }
}
