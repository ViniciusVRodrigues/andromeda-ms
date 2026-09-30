package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.infrastructure.client.AzIdentityDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzLabelDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureReposApiClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Azure DevOps {@code GitPullRequest} → neutral pull request (azure-repos-integration "Pull
 * request sync strategy"). Pure.
 *
 * <ul>
 *   <li>status {@code active → OPEN}, {@code completed → MERGED}, {@code abandoned → CLOSED};</li>
 *   <li>{@code mergedAt} = {@code closedDate} of a completed pull request; {@code closedAt} =
 *       {@code closedDate} of any closed one;</li>
 *   <li>branches without {@code refs/heads/}; {@code draft} from {@code isDraft};</li>
 *   <li>author id normalized (it must equal the voter id of the threads for the "own review"
 *       rule), login = display name, email = {@code uniqueName}.</li>
 * </ul>
 *
 * <p>Azure DevOps has no "last updated" field. {@code updatedAt} is {@code closedDate} for a closed
 * pull request and the observation time for an active one: the neutral sync reads threads only of
 * pull requests updated after the watermark, and an active pull request can gain a vote at any
 * moment (design D2: every active pull request is re-read each sync).
 */
public final class AzurePullRequestMapper {

    private AzurePullRequestMapper() {}

    public static VcsRemotePullRequest map(AzPullRequestDto dto, Instant observedAt) {
        PullRequestStatus status = status(dto.status());
        Instant closedDate = AzureDates.parse(dto.closedDate());
        Instant closedAt = status == PullRequestStatus.OPEN ? null : closedDate;
        Instant mergedAt = status == PullRequestStatus.MERGED ? closedDate : null;
        AzIdentityDto author = dto.createdBy();
        return new VcsRemotePullRequest(
                String.valueOf(dto.pullRequestId()),
                dto.pullRequestId(),
                dto.title(),
                dto.description(),
                status,
                author == null ? null : author.displayName(),
                author == null ? null : AzureIdentityIds.normalize(author.id()),
                AzureDates.parse(dto.creationDate()),
                closedAt != null ? closedAt : observedAt,
                mergedAt,
                closedAt,
                AzureReposApiClient.shortBranch(dto.sourceRefName()),
                AzureReposApiClient.shortBranch(dto.targetRefName()),
                Boolean.TRUE.equals(dto.draft()),
                labels(dto.labels()),
                author == null ? null : author.uniqueName());
    }

    public static PullRequestStatus status(String azureStatus) {
        if (azureStatus == null) return PullRequestStatus.OPEN;
        return switch (azureStatus.toLowerCase(Locale.ROOT)) {
            case "completed" -> PullRequestStatus.MERGED;
            case "abandoned" -> PullRequestStatus.CLOSED;
            default -> PullRequestStatus.OPEN;
        };
    }

    private static List<String> labels(List<AzLabelDto> labels) {
        if (labels == null) return List.of();
        return labels.stream()
                .filter(Objects::nonNull)
                .filter(label -> !Boolean.FALSE.equals(label.active()))
                .map(AzLabelDto::name)
                .filter(Objects::nonNull)
                .toList();
    }
}
