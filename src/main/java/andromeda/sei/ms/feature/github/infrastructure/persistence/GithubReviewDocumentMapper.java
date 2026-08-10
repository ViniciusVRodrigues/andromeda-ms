package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubReview;
import org.springframework.stereotype.Component;

@Component
public class GithubReviewDocumentMapper {

    public GithubReview toDomain(GithubReviewDocument doc) {
        if (doc == null) return null;
        return new GithubReview(
                doc.getId(), doc.getOrganizationId(), doc.getPullRequestId(), doc.getExternalId(),
                doc.getReviewerGithubLogin(), doc.getReviewerUserId(), doc.getResult(), doc.getSubmittedAt(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public GithubReviewDocument toDocument(GithubReview review) {
        if (review == null) return null;
        return GithubReviewDocument.builder()
                .id(review.getId())
                .organizationId(review.getOrganizationId())
                .pullRequestId(review.getPullRequestId())
                .externalId(review.getExternalId())
                .reviewerGithubLogin(review.getReviewerGithubLogin())
                .reviewerUserId(review.getReviewerUserId())
                .result(review.getResult())
                .submittedAt(review.getSubmittedAt())
                .active(review.isActive())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .version(review.getVersion())
                .build();
    }
}
