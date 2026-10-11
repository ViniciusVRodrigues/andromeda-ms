package andromeda.sei.ms.feature.dashboardlayout.domain;

import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutInvalidException;

import java.util.Arrays;

/** Widget widths (design.md D4): P=4, M=6, G=8, I=12 columns. */
public enum WidgetSize {
    P, M, G, I;

    public static WidgetSize parse(String value) {
        return Arrays.stream(values())
                .filter(size -> size.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new DashboardLayoutInvalidException("unknown size " + value));
    }
}
