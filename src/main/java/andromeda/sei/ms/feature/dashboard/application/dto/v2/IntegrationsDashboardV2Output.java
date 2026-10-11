package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import java.time.Instant;
import java.util.List;

/**
 * Integrations and identities of the organization (dashboards-customizaveis D1, {@code mock/integrations.js}
 * of the v2 preview, without the live repository listing, the rate limit, the git author and the
 * configuration panels).
 *
 * <p><b>No secret ever leaves here:</b> no PAT, OAuth access/refresh token, connector token hash,
 * encrypted provider token, password hash or invite token — only states, counts, dates and labels.
 * The only e-mail is the Azure DevOps identity e-mail of each person, for administrators.
 */
public record IntegrationsDashboardV2Output(
        Connections connections,
        List<PersonLinks> people,
        int alertCount,
        List<RepoLink> repos,
        Sync sync,
        UnlinkedCommits unlinkedCommits,
        Instant asOf) {

    public record Connections(AzureConnectionState azure, GithubConnectionState github, AiConnectorState aiConnector) {}

    /** {@code at} is {@code null}: the connection keeps the last error, not when it happened. */
    public record LastError(String code, String message, Instant at) {}

    /**
     * {@code status} {@code CONNECTED}, {@code EXPIRED}, {@code ERROR} or {@code DISCONNECTED} (also
     * when there is no connection, with the other fields {@code null}). {@code tokenExpiresAt} is
     * when the OAuth access token expires (renewed by the refresh token); {@code reposAccess} whether
     * the credential reads Azure Repos ({@code null} while unknown).
     */
    public record AzureConnectionState(String status, String authMethod, String orgUrl, String authorizedBy,
                                       Instant tokenExpiresAt, Instant lastSuccessAt, Integer consecutiveFailures,
                                       LastError lastError, Boolean reposAccess) {}

    public record GithubConnectionState(String status, String authMethod, String authorizedBy, Instant lastSuccessAt,
                                        Integer consecutiveFailures, LastError lastError,
                                        WorkItemLink workItemLink) {}

    /** Pull request ↔ work item link: on/off and the prefixes looked for ({@code AB#} by default). */
    public record WorkItemLink(boolean enabled, List<String> prefixes) {}

    /**
     * Connector tokens of the whole organization; {@code people}: users with an active token;
     * {@code lastUploadAt}: the latest upload of the last 30 days.
     */
    public record AiConnectorState(int activeTokens, int revokedTokens, int people, Instant lastUploadAt) {}

    /** An active user and their links; {@code null} link = not linked. */
    public record PersonLinks(String userId, String name, AzureLink azure, GithubLink github,
                              ConnectorLinks connector, List<Alert> alerts) {}

    public record AzureLink(String email) {}

    /** {@code scopes} empty when unknown; {@code tokenExpiresAt} {@code null} for tokens that never expire. */
    public record GithubLink(String login, List<String> scopes, Instant linkedAt, Instant tokenExpiresAt) {}

    /** {@code labels} of the active tokens (the machine names people gave them). */
    public record ConnectorLinks(int activeTokens, int revokedTokens, List<String> labels, Instant lastUploadAt) {}

    /** {@code tone} {@code info}, {@code warn} or {@code bad}; {@code code} stable, {@code text} pt-BR. */
    public record Alert(String tone, String code, String text) {}

    /** A repository bound to a project (the repositories the token sees but nobody bound are a live query). */
    public record RepoLink(String repositoryId, String repo, String provider, String projectId, String project,
                           boolean syncEnabled, Instant lastSyncAt) {}

    public record Sync(List<SyncDay> byDay, List<SyncTriggers> triggers, List<SyncProblem> problems,
                       SyncToday today) {}

    /**
     * One day of the last 14 (UTC): average and longest run of each integration in seconds
     * ({@code null} without runs), items processed and failed by both.
     */
    public record SyncDay(String date, String label, Double azureAvgSec, Double githubAvgSec, Double azureMaxSec,
                          Double githubMaxSec, int processed, int failedItems) {}

    /** {@code integration} {@code AZURE_DEVOPS} or {@code GITHUB}. */
    public record SyncTriggers(String integration, int scheduled, int manual, int webhook) {}

    public record SyncProblem(String integration, String operation, String status, String trigger, Instant startedAt,
                              Long durationMs, Integer itemsProcessed, Integer itemsFailed, SyncError error) {}

    /** Code and HTTP status only — the stored message and detail stay out. */
    public record SyncError(String code, Integer httpStatus) {}

    public record SyncToday(int runs, int failed) {}

    /** Commits of the last 30 days on bound repositories whose author has no linked user. */
    public record UnlinkedCommits(int total, int withoutUser, Double pct, List<UnlinkedLogin> byLogin) {}

    /** {@code login} of the provider account ({@code null} when unknown); {@code bot}: ends with {@code [bot]}. */
    public record UnlinkedLogin(String login, boolean bot, int commits) {}
}
