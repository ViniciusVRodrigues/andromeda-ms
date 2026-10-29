package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cascading pull request ↔ work item resolution (vcs-work-item-linking): the provider's native
 * links first (Azure Repos), and only when there are none, the text extraction over source branch,
 * then body, then title with the organization's work item patterns.
 *
 * <p>Returns the work item's <em>external</em> id and does not check that it exists: sync order
 * across integrations is not guaranteed, so the read side resolves it to the internal work item,
 * and a reference to an item synced later starts resolving on its own.
 */
public final class WorkItemLinkResolver {

    private WorkItemLinkResolver() {}

    public static Optional<String> resolve(List<String> nativeLinks, WorkItemPatterns patterns,
                                           String sourceBranch, String body, String title) {
        Optional<String> nativeLink = nativeLinks == null ? Optional.empty() : nativeLinks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .findFirst();
        if (nativeLink.isPresent()) return nativeLink;
        return WorkItemReferenceExtractor.extract(patterns, sourceBranch, body, title);
    }
}
