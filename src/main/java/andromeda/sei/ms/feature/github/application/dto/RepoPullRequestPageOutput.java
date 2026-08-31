package andromeda.sei.ms.feature.github.application.dto;

import java.util.List;

public record RepoPullRequestPageOutput(List<RepoPullRequestOutput> content, int page, int size,
                                         long totalElements) {}
