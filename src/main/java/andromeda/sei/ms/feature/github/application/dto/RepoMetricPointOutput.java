package andromeda.sei.ms.feature.github.application.dto;

import java.time.Instant;

public record RepoMetricPointOutput(Instant periodStart, Instant periodEnd, Double cfr,
                                     boolean estimated) {}
