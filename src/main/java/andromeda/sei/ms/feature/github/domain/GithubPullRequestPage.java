package andromeda.sei.ms.feature.github.domain;

import java.util.List;

/** One page of a repository's pull requests, plus the total for the same filter. */
public record GithubPullRequestPage(List<GithubPullRequest> content, int page, int size,
                                     long totalElements) {}
