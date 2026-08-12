package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubReview;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import andromeda.sei.ms.feature.github.domain.ReviewResult;
import andromeda.sei.ms.feature.github.infrastructure.client.GhReviewDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Fetches reviews only for PRs whose {@code updated_at} is newer than the last sync watermark
 * (design.md D5 -- reviews have no bulk endpoint, so scoping to changed PRs bounds the N+1 cost).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncGithubReviewsUseCase {

    private final GithubReviewRepository githubReviewRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;
    private final GithubAuthorResolver githubAuthorResolver;

    public int execute(GithubConnection connection, GithubRepo repo, List<GithubPullRequest> pullRequests,
                        Instant lastSyncAt) {
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        String orgId = connection.getOrganizationId();

        int processed = 0;
        for (GithubPullRequest pr : pullRequests) {
            if (!isUpdatedSinceWatermark(pr, lastSyncAt)) {
                continue;
            }
            try {
                var reviews = githubApiClient.getReviews(repo.getOwner(), repo.getName(), pr.getNumber(), authHeader);
                for (GhReviewDto dto : reviews) {
                    upsertReview(orgId, pr.getId(), dto);
                    processed++;
                }
            } catch (Exception e) {
                log.warn("Failed to sync reviews for PR number={} repo={}: {}",
                        pr.getNumber(), repo.getFullName(), e.getMessage());
            }
        }
        return processed;
    }

    private boolean isUpdatedSinceWatermark(GithubPullRequest pr, Instant lastSyncAt) {
        if (lastSyncAt == null) return true;
        return pr.getExternalUpdatedAt() != null && pr.getExternalUpdatedAt().isAfter(lastSyncAt);
    }

    private void upsertReview(String orgId, String pullRequestId, GhReviewDto dto) {
        String reviewerLogin = dto.user() != null ? dto.user().login() : null;
        Long reviewerGithubNumericId = dto.user() != null ? dto.user().id() : null;
        String reviewerUserId = githubAuthorResolver.resolve(orgId, reviewerGithubNumericId);
        ReviewResult result = parseResult(dto.state());
        Instant submittedAt = parseInstant(dto.submittedAt());
        String externalId = String.valueOf(dto.id());

        var existing = githubReviewRepository.findByOrganizationIdAndPullRequestIdAndExternalId(
                orgId, pullRequestId, externalId);
        Instant now = Instant.now();

        GithubReview review = existing.orElseGet(() -> new GithubReview(
                null, orgId, pullRequestId, externalId, reviewerLogin, reviewerUserId, result, submittedAt,
                true, now, now, null));

        review.setReviewerGithubLogin(reviewerLogin);
        review.setReviewerUserId(reviewerUserId);
        review.setResult(result);
        review.setSubmittedAt(submittedAt);

        githubReviewRepository.save(review);
    }

    private ReviewResult parseResult(String state) {
        if (state == null) return null;
        try {
            return ReviewResult.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
