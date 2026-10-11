package andromeda.sei.ms.feature.dashboardlayout.domain;

import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutViewUnknownException;

import java.util.Arrays;

/** The customizable dashboard views (design.md D2): the "Resumo" of each one. */
public enum DashboardViewKey {
    INDIVIDUAL("individual"),
    EQUIPE("equipe"),
    PROJETO("projeto"),
    ORG("org");

    private final String value;

    DashboardViewKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Exact, case-sensitive match on the wire value; anything else is 422 {@code DASHBOARD_LAYOUT_VIEW_UNKNOWN}. */
    public static DashboardViewKey parse(String value) {
        return Arrays.stream(values())
                .filter(key -> key.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new DashboardLayoutViewUnknownException(value));
    }
}
