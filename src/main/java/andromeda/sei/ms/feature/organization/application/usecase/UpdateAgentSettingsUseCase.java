package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.dto.AgentSettingsOutput;
import andromeda.sei.ms.feature.organization.domain.AgentSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.AgentBranchPatternInvalidException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replaces the organization's branch patterns. {@code null} or empty restores the defaults; an
 * invalid list answers 422 {@code AGENT_BRANCH_PATTERN_INVALID} with the offending index.
 */
@Component
@RequiredArgsConstructor
public class UpdateAgentSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public AgentSettingsOutput execute(String organizationId, List<String> branchPatterns) {
        List<BranchPatternMatcher.Error> errors = BranchPatternMatcher.validateAll(branchPatterns);
        if (!errors.isEmpty()) throw new AgentBranchPatternInvalidException(errors);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        organization.setAgentSettings(new AgentSettings(branchPatterns));
        return AgentSettingsOutput.from(organizationRepository.save(organization).getAgentSettings());
    }
}
