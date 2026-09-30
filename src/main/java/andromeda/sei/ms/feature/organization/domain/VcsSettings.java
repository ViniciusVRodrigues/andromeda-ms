package andromeda.sei.ms.feature.organization.domain;

import java.util.List;

/**
 * Version-control settings of the organization (vcs-work-item-linking), applied to every provider.
 *
 * @param workItemLink      pull request ↔ work item link configuration; {@code null} when the
 *                          organization never configured one — persistence must keep "never
 *                          configured" apart from "configured to the defaults", or saving an
 *                          organization would freeze today's defaults onto it
 * @param serviceIdentities provider identities (id, unique name or display name) the organization
 *                          flags as service accounts, on top of the built-in heuristics
 *                          (azure-repos-provider D5); never {@code null}
 */
public record VcsSettings(WorkItemLinkConfig workItemLink, List<String> serviceIdentities) {

    public VcsSettings {
        serviceIdentities = serviceIdentities == null ? List.of() : List.copyOf(serviceIdentities);
    }

    public VcsSettings(WorkItemLinkConfig workItemLink) {
        this(workItemLink, List.of());
    }

    public static VcsSettings defaults() {
        return new VcsSettings(null, List.of());
    }

    /** The stored configuration, or the defaults ({@code AB#}, enabled) when none was ever set. */
    public WorkItemLinkConfig effectiveWorkItemLink() {
        return WorkItemLinkConfig.orDefaults(workItemLink);
    }

    public VcsSettings withWorkItemLink(WorkItemLinkConfig link) {
        return new VcsSettings(link, serviceIdentities);
    }

    public VcsSettings withServiceIdentities(List<String> identities) {
        return new VcsSettings(workItemLink, identities);
    }
}
