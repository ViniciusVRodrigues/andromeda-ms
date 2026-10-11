package andromeda.sei.ms.feature.dashboardlayout.domain;

import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutInvalidException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validation of a layout without a server-side widget catalog (design.md D3): id
 * formats, no duplicates in widgets ∪ hidden (nor among section ids), a known size and at most
 * {@link #MAX_ITEMS} items. The id length and section caps are defensive bounds on document size.
 */
final class DashboardLayoutRules {

    static final int MAX_ITEMS = 60;
    static final int MAX_SECTIONS = 60;
    static final int MAX_ID_LENGTH = 80;
    private static final Pattern WIDGET_ID = Pattern.compile("^[a-z0-9-]+\\.[a-z0-9-]+$");
    private static final Pattern SECTION_ID = Pattern.compile("^[a-z0-9-]+$");

    private DashboardLayoutRules() {
    }

    static void validate(List<DashboardSection> sections, List<String> hidden) {
        if (sections == null) throw new DashboardLayoutInvalidException("sections is required");
        if (hidden == null) throw new DashboardLayoutInvalidException("hidden is required");
        if (sections.size() > MAX_SECTIONS) throw new DashboardLayoutInvalidException("too many sections");
        Set<String> sectionIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        for (DashboardSection section : sections) {
            validateSection(section, sectionIds, itemIds);
        }
        for (String id : hidden) {
            requireWidgetId(id, itemIds);
        }
        if (itemIds.size() > MAX_ITEMS) {
            throw new DashboardLayoutInvalidException("more than " + MAX_ITEMS + " items");
        }
    }

    private static void validateSection(DashboardSection section, Set<String> sectionIds, Set<String> itemIds) {
        if (section == null) throw new DashboardLayoutInvalidException("null section");
        if (!matches(SECTION_ID, section.id())) throw new DashboardLayoutInvalidException("bad section id");
        if (!sectionIds.add(section.id())) throw new DashboardLayoutInvalidException("duplicate section " + section.id());
        if (section.widgets() == null) throw new DashboardLayoutInvalidException("section without widgets " + section.id());
        for (DashboardWidget widget : section.widgets()) {
            if (widget == null || widget.size() == null) throw new DashboardLayoutInvalidException("widget without size");
            requireWidgetId(widget.id(), itemIds);
        }
    }

    private static void requireWidgetId(String id, Set<String> itemIds) {
        if (!matches(WIDGET_ID, id)) throw new DashboardLayoutInvalidException("bad widget id");
        if (!itemIds.add(id)) throw new DashboardLayoutInvalidException("duplicate widget " + id);
    }

    private static boolean matches(Pattern pattern, String id) {
        return id != null && id.length() <= MAX_ID_LENGTH && pattern.matcher(id).matches();
    }
}
