package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureProjectOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListAzureProjectsUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;

    /**
     * @param forProjectId when present, ADO projects actively bound to a DIFFERENT Andromeda
     *                     project are excluded, while the one bound to {@code forProjectId}
     *                     itself is kept (preselection when editing); when absent the list is
     *                     unfiltered — byte-for-byte today's behavior (design.md D4).
     */
    public List<AzureProjectOutput> execute(String forProjectId) {
        String orgId = TenantContextHolder.get();
        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        Instant startedAt = Instant.now();
        String authHeader = authStrategyResolver.resolve(connection);

        try {
            List<AzureProjectOutput> projects = azureDevOpsClient
                    .getProjects(connection.getAzureOrgUrl(), authHeader)
                    .stream()
                    .map(p -> new AzureProjectOutput(p.id(), p.name()))
                    .toList();

            if (forProjectId != null) {
                projects = applyExclusivityFilter(orgId, forProjectId, projects);
            }

            log.info("Azure projects fetched: org={} count={} projects={}",
                    orgId, projects.size(), projects.stream().map(AzureProjectOutput::azureProjectName).toList());

            writeSyncLog(orgId, startedAt, SyncStatus.SUCCESS, projects.size(), null, null, null, null);
            return projects;

        } catch (WebClientResponseException e) {
            String body = e.getResponseBodyAsString();
            String detail = body.length() > 2000 ? body.substring(0, 2000) : body;
            log.error("Azure list projects failed: org={} httpStatus={} body={}", orgId, e.getStatusCode().value(), detail);
            writeSyncLog(orgId, startedAt, SyncStatus.FAILED, 0,
                    "AZURE_LIST_PROJECTS_FAILED", e.getMessage(), e.getStatusCode().value(), detail);
            throw e;
        } catch (Exception e) {
            log.error("Azure list projects failed: org={}", orgId, e);
            writeSyncLog(orgId, startedAt, SyncStatus.FAILED, 0, "AZURE_LIST_PROJECTS_FAILED", e.getMessage(), null, null);
            throw e;
        }
    }

    /**
     * Drops every ADO project actively bound to a project other than {@code forProjectId};
     * the one bound to {@code forProjectId} itself stays so the modal can preselect it
     * (design.md D4).
     */
    private List<AzureProjectOutput> applyExclusivityFilter(String orgId, String forProjectId,
                                                             List<AzureProjectOutput> projects) {
        Map<String, String> boundProjectByAzureProjectId = azureProjectBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId)
                .stream()
                .filter(b -> b.getAzureProjectId() != null)
                .collect(Collectors.toMap(
                        AzureProjectBinding::getAzureProjectId,
                        AzureProjectBinding::getProjectId,
                        (a, b) -> a));

        return projects.stream()
                .filter(p -> {
                    String boundToProjectId = boundProjectByAzureProjectId.get(p.azureProjectId());
                    return boundToProjectId == null || boundToProjectId.equals(forProjectId);
                })
                .toList();
    }

    private void writeSyncLog(String orgId, Instant startedAt, SyncStatus status, int count,
                               String errorCode, String errorMessage, Integer httpStatus, String errorDetail) {
        Instant finishedAt = Instant.now();
        IntegrationSyncLog entry = new IntegrationSyncLog(
                null, orgId, Integration.AZURE_DEVOPS, SyncOperation.LIST_PROJECTS,
                status, SyncTrigger.MANUAL, null, startedAt, finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(), count, 0, errorCode, errorMessage);
        entry.setHttpStatus(httpStatus);
        entry.setErrorDetail(errorDetail);
        syncLogRepository.save(entry);
    }
}
