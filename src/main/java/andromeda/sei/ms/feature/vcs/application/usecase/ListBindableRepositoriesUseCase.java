package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput.Source;
import andromeda.sei.ms.feature.vcs.application.port.VcsBindableRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repositories of a provider available to bind: the union of what the connection lists now
 * ({@code LIVE}) and what earlier syncs stored ({@code CACHED}), de-duplicated by external id (display
 * name only for repositories pending first sync) with LIVE winning. Every entry says whether it is bindable; a repository actively bound to a project
 * carries that project id. A failed live listing is logged ({@code LIST_REPOS}) and rethrown: a dead
 * credential must stay visible, there is no cached-only fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListBindableRepositoriesUseCase {

    static final String LIST_FAILED = "VCS_LIST_REPOS_FAILED";

    private final VcsProviderRegistry providerRegistry;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsRepoBindingRepository bindingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    public List<BindableRepositoryOutput> execute(VcsProvider provider) {
        String orgId = TenantContextHolder.get();
        VcsCredentials credentials = providerRegistry.credentialsProvider(provider).credentials(orgId)
                .orElseThrow(() -> new VcsConnectionNotFoundException(provider.name()));
        Instant startedAt = Instant.now();
        try {
            List<BindableRepositoryOutput> repositories = list(orgId, provider, credentials);
            writeLog(orgId, provider, startedAt, SyncStatus.SUCCESS, repositories.size(), null, null);
            return repositories;
        } catch (RuntimeException e) {
            log.error("{} list repositories failed: org={}", provider, orgId, e);
            writeLog(orgId, provider, startedAt, SyncStatus.FAILED, 0, LIST_FAILED, e.getMessage());
            throw e;
        }
    }

    private List<BindableRepositoryOutput> list(String orgId, VcsProvider provider, VcsCredentials credentials) {
        List<VcsRepository> stored = repositoryRepository.findAllByOrganizationIdAndProvider(orgId, provider);
        Map<String, String> projectByRepositoryId = bindingRepository
                .findAllActiveByOrganizationIdAndProvider(orgId, provider).stream()
                .collect(Collectors.toMap(VcsRepoBinding::getRepositoryId, VcsRepoBinding::getProjectId, (a, b) -> a));
        Map<String, String> projectByDisplayName = new LinkedHashMap<>();
        Map<String, String> projectByExternalId = new LinkedHashMap<>();
        stored.forEach(repo -> {
            String projectId = projectByRepositoryId.get(repo.getId());
            if (projectId == null) return;
            if (repo.getExternalRepoId() != null) {
                projectByExternalId.put(repo.getExternalRepoId(), projectId);
            } else if (repo.getDisplayName() != null) {
                /* Only a repository pending first sync is matched by name (names repeat across ADO projects). */
                projectByDisplayName.put(repo.getDisplayName(), projectId);
            }
        });

        Map<String, BindableRepositoryOutput> byKey = new LinkedHashMap<>();
        Set<String> liveNames = new HashSet<>();
        for (VcsBindableRepository live : providerRegistry.client(provider).listBindableRepositories(credentials)) {
            String bound = live.externalRepoId() != null && projectByExternalId.containsKey(live.externalRepoId())
                    ? projectByExternalId.get(live.externalRepoId())
                    : projectByDisplayName.get(live.displayName());
            liveNames.add(live.displayName());
            byKey.putIfAbsent(keyOf(live.externalRepoId(), live.displayName()), new BindableRepositoryOutput(provider,
                    live.externalRepoId(), live.displayName(), live.externalProjectId(), live.externalProjectName(),
                    live.defaultBranch(), live.bindable() && bound == null, bound, Source.LIVE));
        }
        for (VcsRepository repo : stored) {
            if (repo.getDisplayName() == null) continue;
            /* A repository pending first sync has no external id yet: it matches a live one by name. */
            if (repo.getExternalRepoId() == null && liveNames.contains(repo.getDisplayName())) continue;
            String bound = projectByRepositoryId.get(repo.getId());
            byKey.putIfAbsent(keyOf(repo.getExternalRepoId(), repo.getDisplayName()), new BindableRepositoryOutput(
                    provider, repo.getExternalRepoId(), repo.getDisplayName(), repo.getExternalProjectId(), null,
                    repo.getDefaultBranch(), bound == null, bound, Source.CACHED));
        }
        return List.copyOf(byKey.values());
    }

    /**
     * Entries are keyed by external id: Azure Repos names repeat across ADO projects. Only a
     * repository without an id (pending first sync) falls back to its display name.
     */
    private static String keyOf(String externalRepoId, String displayName) {
        return externalRepoId != null ? "id:" + externalRepoId : "name:" + displayName;
    }

    private void writeLog(String orgId, VcsProvider provider, Instant startedAt, SyncStatus status, int count,
                          String errorCode, String errorMessage) {
        Instant finishedAt = Instant.now();
        syncLogRepository.save(new IntegrationSyncLog(null, orgId, VcsIntegrations.integrationOf(provider),
                SyncOperation.LIST_REPOS, status, SyncTrigger.MANUAL, null, startedAt, finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(), count, 0, errorCode, errorMessage));
    }
}
