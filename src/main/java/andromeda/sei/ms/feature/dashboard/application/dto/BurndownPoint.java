package andromeda.sei.ms.feature.dashboard.application.dto;

import java.time.LocalDate;

public record BurndownPoint(LocalDate date, double remainingSp, double idealSp) {}
