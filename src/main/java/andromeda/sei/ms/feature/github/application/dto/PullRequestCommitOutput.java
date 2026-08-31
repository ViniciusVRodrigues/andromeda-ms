package andromeda.sei.ms.feature.github.application.dto;

import java.time.Instant;

public record PullRequestCommitOutput(String sha, String message, String authorLogin,
                                       Instant authoredAt) {}
