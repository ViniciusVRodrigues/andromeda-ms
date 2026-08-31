package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.WorkItemLinkConfigInput;
import andromeda.sei.ms.feature.github.application.dto.WorkItemLinkConfigOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stores the organization's work item linking rules. */
@Component
@RequiredArgsConstructor
public class UpdateWorkItemLinkConfigUseCase {

    private final GithubConnectionRepository githubConnectionRepository;

    public WorkItemLinkConfigOutput execute(WorkItemLinkConfigInput input) {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        // Blank entries would widen every reference to a bare number, which is exactly the
        // ambiguity the prefix requirement exists to prevent.
        List<String> prefixes = input.prefixes() == null ? List.of()
                : input.prefixes().stream().map(String::trim).filter(p -> !p.isBlank()).toList();

        connection.setWorkItemLinkConfig(new WorkItemLinkConfig(input.enabled(), prefixes));
        GithubConnection saved = githubConnectionRepository.save(connection);

        WorkItemLinkConfig stored = saved.getWorkItemLinkConfig();
        return new WorkItemLinkConfigOutput(stored.isEnabled(), stored.getPrefixes());
    }
}
