package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Subdocumento embutido em {@code organizations.vcsSettings}. {@code workItemLink} nulo significa
 * "nunca configurado" (o dominio le os defaults).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VcsSettingsDocument {

    private WorkItemLinkDocument workItemLink;
    /** Identidades de servico da organizacao (azure-repos-provider D5); nulo = nenhuma. */
    private List<String> serviceIdentities;

    public VcsSettingsDocument(WorkItemLinkDocument workItemLink) {
        this.workItemLink = workItemLink;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkItemLinkDocument {
        private boolean enabled;
        private List<String> prefixes;
    }
}
