package andromeda.sei.ms.feature.vcs.application.dto;

import java.time.Instant;

public record PullRequestCommitOutput(String sha, String message, String authorLogin, Instant authoredAt) {}
