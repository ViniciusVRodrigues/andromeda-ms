package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProject;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProjectRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.domain.exception.AzureEntitlementsForbiddenException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoUserEntitlementDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamMemberDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureProjectDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches and persists an organization's Azure DevOps projects and org members immediately
 * after a connection is verified (PAT or Entra ID), independent of any project binding.
 *
 * <p>Runs synchronously on the calling thread. Azure DevOps orgs realistically fit within a
 * handful of projects/teams for Andromeda's current customer profile, so a live round-trip here
 * (bounded by {@link AzureDevOpsClient}'s own retry/backoff) does not meaningfully delay the
 * connect request. Callers MUST catch and log-and-continue on failure (see
 * {@link ConfigureAzureDevOpsPatUseCase} and the OAuth2 success handler) — a failure here must
 * never roll back or block the connection's {@code CONNECTED} status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAzureOrgLevelDataUseCase {

    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final AzureOrgProjectRepository azureOrgProjectRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final AutoLinkAzureIdentitiesUseCase autoLinkAzureIdentitiesUseCase;

    public void execute(AzureConnection connection) {
        String orgId = connection.getOrganizationId();
        String authHeader = authStrategyResolver.resolve(connection);
        String orgUrl = connection.getAzureOrgUrl();

        List<AzureProjectDto> adoProjects = azureDevOpsClient.getProjects(orgUrl, authHeader);
        persistProjects(orgId, adoProjects);
        persistUsers(orgId, orgUrl, authHeader, adoProjects);

        connection.setOrgLevelSyncedAt(Instant.now());
        azureConnectionRepository.save(connection);

        try {
            autoLinkAzureIdentitiesUseCase.executeForOrganization(orgId);
        } catch (RuntimeException e) {
            log.warn("Azure auto-link failed after org-level sync: org={}: {}", orgId, e.getClass().getSimpleName());
        }

        log.info("Azure org-level sync completed: org={} projects={}", orgId, adoProjects.size());
    }

    private void persistProjects(String orgId, List<AzureProjectDto> adoProjects) {
        Instant now = Instant.now();
        List<AzureOrgProject> toSave = adoProjects.stream()
                .map(p -> new AzureOrgProject(null, orgId, p.id(), p.name(), true, now, now, null))
                .toList();
        azureOrgProjectRepository.saveAll(toSave);
    }

    private void persistUsers(String orgId, String orgUrl, String authHeader, List<AzureProjectDto> adoProjects) {
        Map<String, AzureOrgUser> byAzureId = new LinkedHashMap<>();
        for (AzureOrgUser existing : azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId)) {
            byAzureId.put(existing.getAzureId(), existing);
        }
        Instant now = Instant.now();

        for (AzureProjectDto project : adoProjects) {
            List<AdoTeamDto> teams = azureDevOpsClient.getTeams(orgUrl, project.id(), authHeader);
            for (AdoTeamDto team : teams) {
                List<AdoTeamMemberDto> members =
                        azureDevOpsClient.getTeamMembers(orgUrl, project.id(), team.id(), authHeader);
                for (AdoTeamMemberDto member : members) {
                    if (member.id() != null) {
                        observe(byAzureId, orgId, member.id(), member.displayName(), member.uniqueName(),
                                member.uniqueName(), AzureOrgUserSource.TEAM, now);
                    }
                }
            }
        }

        try {
            for (AdoUserEntitlementDto e : azureDevOpsClient.listUserEntitlements(orgUrl, authHeader)) {
                if (e.id() != null) {
                    observe(byAzureId, orgId, e.id(), e.displayName(), e.mailAddress(), e.principalName(),
                            AzureOrgUserSource.DIRECTORY, now);
                }
            }
        } catch (AzureEntitlementsForbiddenException e) {
            log.warn("Azure user directory not readable, continuing with team members only: org={}", orgId);
        }

        azureOrgUserRepository.saveAll(List.copyOf(byAzureId.values()));
    }

    private static void observe(Map<String, AzureOrgUser> byAzureId, String orgId, String azureId,
                                String displayName, String email, String principalName,
                                AzureOrgUserSource source, Instant now) {
        AzureOrgUser user = byAzureId.computeIfAbsent(azureId, id ->
                new AzureOrgUser(null, orgId, id, null, null, null, null, true, now, now, null));
        user.mergeFrom(displayName, email, principalName, source);
    }
}
