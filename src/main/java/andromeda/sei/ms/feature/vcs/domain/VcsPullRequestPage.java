package andromeda.sei.ms.feature.vcs.domain;

import java.util.List;

/** One page of a repository's pull requests, plus the total for the same filter. */
public record VcsPullRequestPage(List<VcsPullRequest> content, int page, int size, long totalElements) {}
