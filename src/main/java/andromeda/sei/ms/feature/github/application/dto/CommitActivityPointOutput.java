package andromeda.sei.ms.feature.github.application.dto;

import java.time.Instant;

public record CommitActivityPointOutput(Instant periodStart, Instant periodEnd, long commitCount) {}
