package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Set;

/**
 * {@link AzureDevOpsAccessProbe} over the Boards and Git clients. The header-based methods are
 * also used by the Azure Repos adapter to tell a missing Code (Read) scope (projects allowed,
 * repositories denied) from a dead credential (both denied).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureDevOpsAccessProbeAdapter implements AzureDevOpsAccessProbe {

    /** 404 on {@code _apis/projects} means the organization in the URL does not exist for the credential. */
    private static final Set<Integer> PROJECTS_DENIED = Set.of(401, 403, 404);
    private static final Set<Integer> REPOS_DENIED = Set.of(401, 403);

    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureReposApiClient azureReposApiClient;
    private final AzureAuthStrategyResolver authStrategyResolver;

    @Override
    public AzureProjectsAccess projects(AzureConnection connection) {
        return projects(connection.getAzureOrgUrl(), authStrategyResolver.resolve(connection));
    }

    @Override
    public Boolean reposAccess(AzureConnection connection) {
        return reposAccess(connection.getAzureOrgUrl(), authStrategyResolver.resolve(connection));
    }

    public AzureProjectsAccess projects(String orgUrl, String authHeader) {
        try {
            azureDevOpsClient.getProjects(orgUrl, authHeader);
            return AzureProjectsAccess.GRANTED;
        } catch (WebClientResponseException e) {
            if (PROJECTS_DENIED.contains(e.getStatusCode().value())) return AzureProjectsAccess.DENIED;
            throw e;
        } catch (RuntimeException e) {
            if (isSignInPage(e)) return AzureProjectsAccess.DENIED;
            throw e;
        }
    }

    public Boolean reposAccess(String orgUrl, String authHeader) {
        try {
            azureReposApiClient.listRepositories(orgUrl, authHeader);
            return Boolean.TRUE;
        } catch (WebClientResponseException e) {
            if (isDenied(e)) return Boolean.FALSE;
            log.warn("Azure Repos access check failed with HTTP {}: {}", e.getStatusCode().value(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            if (isSignInPage(e)) return Boolean.FALSE;
            log.warn("Azure Repos access check failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * A rejected Basic credential may be answered with {@code 203} and the HTML sign-in page instead
     * of a 401; the JSON decoder then fails. Treated as a denial (to confirm in the live spike).
     */
    public static boolean isSignInPage(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof DecodingException) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    public static boolean isDenied(WebClientResponseException e) {
        return REPOS_DENIED.contains(e.getStatusCode().value());
    }
}
