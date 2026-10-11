package andromeda.sei.ms.feature.dashboardlayout.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fixed section of a view, with the user's widget order and collapsed state (design.md D4).
 * A null widget list is kept as null so validation can reject it.
 */
public record DashboardSection(String id, boolean collapsed, List<DashboardWidget> widgets) {

    public DashboardSection {
        widgets = widgets == null ? null : Collections.unmodifiableList(new ArrayList<>(widgets));
    }
}
