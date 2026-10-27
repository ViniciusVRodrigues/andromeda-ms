package andromeda.sei.ms.feature.dashboard.application.dto;

import java.time.LocalDate;

/** One day of the burndown, in points of complexity (complexity-effort D6). */
public record BurndownPoint(LocalDate date, double remainingPoints, double idealPoints) {}
