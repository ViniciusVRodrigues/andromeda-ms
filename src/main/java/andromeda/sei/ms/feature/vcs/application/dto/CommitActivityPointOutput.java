package andromeda.sei.ms.feature.vcs.application.dto;

import java.time.Instant;

public record CommitActivityPointOutput(Instant periodStart, Instant periodEnd, long commitCount) {}
