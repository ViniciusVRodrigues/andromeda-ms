package andromeda.sei.ms.feature.dashboard.application.dto;

import java.time.LocalDate;

/**
 * One day of the burndown, in points of complexity. {@code remainingPoints}/{@code idealPoints} repeat
 * {@code remainingSp}/{@code idealSp} under the contract names (complexity-effort D6); the old names stay
 * for one release.
 */
public record BurndownPoint(LocalDate date, double remainingSp, double idealSp,
                            double remainingPoints, double idealPoints) {

    public BurndownPoint(LocalDate date, double remainingSp, double idealSp) {
        this(date, remainingSp, idealSp, remainingSp, idealSp);
    }
}
