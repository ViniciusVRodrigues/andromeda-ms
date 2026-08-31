package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.WorkItemLinkConfigOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads the organization's work item linking rules, falling back to the defaults. */
@Component
@RequiredArgsConstructor
public class GetWorkItemLinkConfigUseCase {

    private final GithubConnectionRepository githubConnectionRepository;

    public WorkItemLinkConfigOutput execute() {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        WorkItemLinkConfig config = connection.getWorkItemLinkConfig();
        return new WorkItemLinkConfigOutput(config.isEnabled(), config.getPrefixes());
    }
}
