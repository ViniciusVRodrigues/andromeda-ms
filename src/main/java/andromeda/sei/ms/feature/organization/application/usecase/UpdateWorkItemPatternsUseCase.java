package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.organization.domain.exception.WorkItemPatternInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Replaces the organization's work item patterns (mode CUSTOM, audited). A null or empty list
 * restores the defaults (mode DEFAULT); an invalid list answers 422 {@code WORK_ITEM_PATTERN_INVALID}.
 */
@Component
@RequiredArgsConstructor
public class UpdateWorkItemPatternsUseCase {

    /** {@code alsoInText} null = the preset default (true for AB_HASH and PREFIX). */
    public record PatternInput(String regex, Boolean alsoInText) {}

    private final OrganizationRepository organizationRepository;

    public WorkItemPatterns execute(String organizationId, String userId, boolean enabled, List<PatternInput> patterns) {
        List<PatternInput> input = patterns == null ? List.of() : patterns;
        List<BranchPatternMatcher.Error> errors = BranchPatternMatcher.validateAll(
                input.stream().map(p -> p == null ? null : p.regex()).toList());
        if (!errors.isEmpty()) throw new WorkItemPatternInvalidException(errors);

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        Instant now = Instant.now();
        WorkItemPatterns current = organization.getWorkItemPatterns();
        WorkItemPatterns updated;
        if (input.isEmpty()) {
            updated = new WorkItemPatterns(enabled, WorkItemPatterns.Mode.DEFAULT, null, null, null, userId, now,
                    current.detection());
        } else {
            List<WorkItemPatterns.Entry> entries = input.stream()
                    .map(p -> WorkItemPatterns.Entry.of(p.regex(), p.alsoInText() != null
                            ? p.alsoInText() : WorkItemPatterns.Entry.defaultAlsoInText(p.regex())))
                    .toList();
            updated = new WorkItemPatterns(enabled, WorkItemPatterns.Mode.CUSTOM, entries, userId, now, userId, now,
                    current.detection());
        }
        organization.setWorkItemPatterns(updated);
        return organizationRepository.save(organization).getWorkItemPatterns();
    }
}
