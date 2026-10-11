package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One customized dashboard view per (organization, user, viewKey) while active (design.md D6).
 * The unique index is partial on {@code active: true}, so a soft-deleted layout ("restore default")
 * does not block creating a new one.
 */
@Document(collection = "dashboard_layouts")
@CompoundIndexes({
        @CompoundIndex(name = DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX,
                def = "{'organizationId': 1, 'userId': 1, 'viewKey': 1}",
                unique = true,
                partialFilter = "{'active': true}"),
        @CompoundIndex(name = DashboardLayoutDocument.ORG_ACTIVE_INDEX,
                def = "{'organizationId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutDocument {

    static final String UNIQUE_ACTIVE_INDEX = "uk_org_user_view_active";
    static final String ORG_ACTIVE_INDEX = "idx_org_active";

    @Id
    private String id;

    private String organizationId;
    private String userId;
    /** Wire value of the view: individual, equipe, projeto or org. */
    private String viewKey;
    @Builder.Default
    private List<SectionDocument> sections = new ArrayList<>();
    @Builder.Default
    private List<String> hidden = new ArrayList<>();

    @Builder.Default
    private boolean active = true;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionDocument {
        private String id;
        private boolean collapsed;
        private List<WidgetDocument> widgets = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WidgetDocument {
        private String id;
        private String size;
    }
}
