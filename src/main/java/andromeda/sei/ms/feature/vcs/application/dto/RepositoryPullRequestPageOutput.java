package andromeda.sei.ms.feature.vcs.application.dto;

import java.util.List;

public record RepositoryPullRequestPageOutput(List<RepositoryPullRequestOutput> content, int page, int size,
                                              long totalElements) {}
