package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.AiConnectorState;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.Alert;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.AzureConnectionState;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.AzureLink;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.ConnectorLinks;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.Connections;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.GithubConnectionState;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.GithubLink;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.LastError;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.PersonLinks;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.RepoLink;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.UnlinkedCommits;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.UnlinkedLogin;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.WorkItemLink;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.dashboard.application.v2.SyncRunsSummary;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integrations and identities v2 (dashboards-customizaveis D1, {@code mock/integrations.js}): the
 * state of the connections, every active person × their links, the sync runs of the last 14 days,
 * the latest problems and the unlinked commits of the last 30 days — one query per source, all
 * scoped by {@code organizationId}.
 *
 * <p>Secrets are never copied into the output: the connections, provider accounts and connector
 * tokens are read here but only states, dates, counts, logins and labels leave. Sync problems carry
 * the error code and HTTP status, never the stored message or detail. Nothing is logged.
 */
@Service
public class GetIntegrationsDashboardV2UseCase {

    static final int UNLINKED_WINDOW_DAYS = 30;
    static final int UPLOAD_WINDOW_DAYS = 30;
    static final int TOP_UNLINKED_LOGINS = 8;
    static final Duration CONNECTOR_STALE_AFTER = Duration.ofDays(3);
    static final Duration TOKEN_EXPIRING_WITHIN = Duration.ofDays(7);
    private static final String READ_ORG_SCOPE = "read:org";
    private static final String BOT_SUFFIX = "[bot]";
    private static final String GITHUB_AUTH_METHOD = "PAT";
    private static final String DISCONNECTED = "DISCONNECTED";
    private static final String INFO = "info";
    private static final String WARN = "warn";
    private static final String BAD = "bad";
    private static final long SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    private final UserRepository userRepository;
    private final UserProviderAccountRepository providerAccountRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final ConnectorTokenRepository connectorTokenRepository;
    private final AIUsageRepository aiUsageRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final OrganizationRepository organizationRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsCommitRepository commitRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    @Autowired
    public GetIntegrationsDashboardV2UseCase(UserRepository userRepository,
                                             UserProviderAccountRepository providerAccountRepository,
                                             AzureOrgUserRepository azureOrgUserRepository,
                                             ConnectorTokenRepository connectorTokenRepository,
                                             AIUsageRepository aiUsageRepository,
                                             AzureConnectionRepository azureConnectionRepository,
                                             GithubConnectionRepository githubConnectionRepository,
                                             OrganizationRepository organizationRepository,
                                             IntegrationSyncLogRepository syncLogRepository,
                                             VcsRepoBindingRepository bindingRepository,
                                             VcsRepositoryRepository repositoryRepository,
                                             VcsCommitRepository commitRepository,
                                             ProjectRepository projectRepository) {
        this(userRepository, providerAccountRepository, azureOrgUserRepository, connectorTokenRepository,
                aiUsageRepository, azureConnectionRepository, githubConnectionRepository, organizationRepository,
                syncLogRepository, bindingRepository, repositoryRepository, commitRepository, projectRepository,
                Clock.systemUTC());
    }

    GetIntegrationsDashboardV2UseCase(UserRepository userRepository,
                                      UserProviderAccountRepository providerAccountRepository,
                                      AzureOrgUserRepository azureOrgUserRepository,
                                      ConnectorTokenRepository connectorTokenRepository,
                                      AIUsageRepository aiUsageRepository,
                                      AzureConnectionRepository azureConnectionRepository,
                                      GithubConnectionRepository githubConnectionRepository,
                                      OrganizationRepository organizationRepository,
                                      IntegrationSyncLogRepository syncLogRepository,
                                      VcsRepoBindingRepository bindingRepository,
                                      VcsRepositoryRepository repositoryRepository,
                                      VcsCommitRepository commitRepository, ProjectRepository projectRepository,
                                      Clock clock) {
        this.userRepository = userRepository;
        this.providerAccountRepository = providerAccountRepository;
        this.azureOrgUserRepository = azureOrgUserRepository;
        this.connectorTokenRepository = connectorTokenRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.azureConnectionRepository = azureConnectionRepository;
        this.githubConnectionRepository = githubConnectionRepository;
        this.organizationRepository = organizationRepository;
        this.syncLogRepository = syncLogRepository;
        this.bindingRepository = bindingRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    public IntegrationsDashboardV2Output execute(String orgId) {
        Objects.requireNonNull(orgId, "orgId");
        Instant now = clock.instant();
        Map<String, User> users = userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
        Optional<AzureConnection> azure = azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);
        Optional<GithubConnection> github = githubConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);
        Map<String, List<ConnectorToken>> tokens = connectorTokenRepository.findAllByOrganizationId(orgId).stream()
                .filter(t -> t.getUserId() != null).collect(Collectors.groupingBy(ConnectorToken::getUserId));
        Map<String, Instant> lastUpload = lastUploadByUser(orgId, users.values().stream().filter(User::isActive)
                .map(User::getId).toList(), now);
        List<PersonLinks> people = people(orgId, users, tokens, lastUpload, azure.isPresent(), github.isPresent(), now);
        List<VcsRepoBinding> bindings = bindingRepository.findAllActiveByOrganizationId(orgId);
        Map<String, VcsRepository> repositories = repositories(orgId, bindings);
        return new IntegrationsDashboardV2Output(
                new Connections(azureState(azure, users), githubState(orgId, github, users),
                        aiConnector(tokens, lastUpload)),
                people, alertCount(people), repoLinks(orgId, bindings, repositories),
                SyncRunsSummary.of(syncLogs(orgId, now), SprintWindow.toDay(now)),
                unlinkedCommits(orgId, repositories.keySet(), now), now);
    }

    // ── connections ──

    private static AzureConnectionState azureState(Optional<AzureConnection> connection, Map<String, User> users) {
        if (connection.isEmpty()) {
            return new AzureConnectionState(DISCONNECTED, null, null, null, null, null, null, null, null);
        }
        AzureConnection c = connection.get();
        return new AzureConnectionState(c.getStatus() != null ? c.getStatus().name() : AzureConnectionStatus.DISCONNECTED.name(),
                c.getAuthMethod() != null ? c.getAuthMethod().name() : null, c.getAzureOrgUrl(),
                nameOf(users, c.getAuthorizedByUserId()), c.getTokenExpiresAt(), c.getLastSuccessAt(),
                c.getConsecutiveFailures(), lastError(c.getLastErrorCode(), c.getLastErrorMessage()), c.getReposAccess());
    }

    private GithubConnectionState githubState(String orgId, Optional<GithubConnection> connection,
                                              Map<String, User> users) {
        if (connection.isEmpty()) {
            return new GithubConnectionState(DISCONNECTED, null, null, null, null, null, workItemLink(orgId));
        }
        GithubConnection c = connection.get();
        return new GithubConnectionState(c.getStatus() != null ? c.getStatus().name() : DISCONNECTED,
                GITHUB_AUTH_METHOD, nameOf(users, c.getAuthorizedByUserId()), c.getLastSuccessAt(),
                c.getConsecutiveFailures(), lastError(c.getLastErrorCode(), c.getLastErrorMessage()),
                workItemLink(orgId));
    }

    private WorkItemLink workItemLink(String orgId) {
        WorkItemPatterns patterns = organizationRepository.findById(orgId)
                .map(Organization::getWorkItemPatterns).orElseGet(WorkItemPatterns::defaults);
        return new WorkItemLink(patterns.enabled(), patterns.legacyPrefixes());
    }

    private static LastError lastError(String code, String message) {
        return code == null ? null : new LastError(code, message, null);
    }

    private static AiConnectorState aiConnector(Map<String, List<ConnectorToken>> tokens, Map<String, Instant> lastUpload) {
        List<ConnectorToken> all = tokens.values().stream().flatMap(List::stream).toList();
        int people = (int) tokens.values().stream().filter(list -> list.stream().anyMatch(ConnectorToken::isActive)).count();
        Instant latest = lastUpload.values().stream().max(Comparator.naturalOrder()).orElse(null);
        return new AiConnectorState((int) all.stream().filter(ConnectorToken::isActive).count(),
                (int) all.stream().filter(t -> !t.isActive()).count(), people, latest);
    }

    /** Latest upload per active user over the last 30 days (by user id and day, the indexed path). */
    private Map<String, Instant> lastUploadByUser(String orgId, List<String> userIds, Instant now) {
        if (userIds.isEmpty()) return Map.of();
        LocalDate today = SprintWindow.toDay(now);
        Map<String, Instant> byUser = new HashMap<>();
        for (AIUsage usage : aiUsageRepository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(orgId, userIds,
                today.minusDays(UPLOAD_WINDOW_DAYS - 1L), today)) {
            if (usage.getUserId() == null || usage.getUploadedAt() == null) continue;
            byUser.merge(usage.getUserId(), usage.getUploadedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return byUser;
    }

    // ── people × links ──

    private List<PersonLinks> people(String orgId, Map<String, User> users, Map<String, List<ConnectorToken>> tokens,
                                     Map<String, Instant> lastUpload, boolean azureConnected, boolean githubConnected,
                                     Instant now) {
        Map<String, UserProviderAccount> githubAccounts = providerAccountRepository
                .findAllByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(a -> a.getProvider() == Provider.GITHUB && a.getUserId() != null)
                .collect(Collectors.toMap(UserProviderAccount::getUserId, Function.identity(), (a, b) -> a));
        Map<String, String> azureEmails = azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(u -> u.getAzureId() != null && u.getAzureEmail() != null)
                .collect(Collectors.toMap(AzureOrgUser::getAzureId, AzureOrgUser::getAzureEmail, (a, b) -> a));
        return users.values().stream().filter(User::isActive)
                .map(user -> {
                    AzureLink azure = azureLink(user, azureEmails);
                    GithubLink github = githubLink(user, githubAccounts.get(user.getId()));
                    ConnectorLinks connector = connectorLinks(tokens.getOrDefault(user.getId(), List.of()),
                            lastUpload.get(user.getId()));
                    return new PersonLinks(user.getId(), displayName(user), azure, github, connector,
                            alerts(azure, github, connector, azureConnected, githubConnected, now));
                })
                .toList();
    }

    private static AzureLink azureLink(User user, Map<String, String> azureEmails) {
        if (!present(user.getAzureId())) return null;
        return new AzureLink(present(user.getAzureEmail()) ? user.getAzureEmail() : azureEmails.get(user.getAzureId()));
    }

    private static GithubLink githubLink(User user, UserProviderAccount account) {
        if (account != null) {
            return new GithubLink(account.getExternalLogin(), account.getScopes() == null ? List.of() : account.getScopes(),
                    account.getLinkedAt(), account.getTokenExpiresAt());
        }
        return present(user.getGithubId()) ? new GithubLink(null, List.of(), null, null) : null;
    }

    private static ConnectorLinks connectorLinks(List<ConnectorToken> tokens, Instant lastUploadAt) {
        List<ConnectorToken> active = tokens.stream().filter(ConnectorToken::isActive).toList();
        return new ConnectorLinks(active.size(), tokens.size() - active.size(),
                active.stream().map(ConnectorToken::getLabel).filter(Objects::nonNull).toList(), lastUploadAt);
    }

    /** The preview's alerts; a missing link only counts when the organization uses that provider. */
    static List<Alert> alerts(AzureLink azure, GithubLink github, ConnectorLinks connector, boolean azureConnected,
                              boolean githubConnected, Instant now) {
        List<Alert> alerts = new ArrayList<>();
        if (azure == null && azureConnected) alerts.add(new Alert(WARN, "AZURE_NOT_LINKED", "sem vínculo com o Azure DevOps"));
        if (github == null && githubConnected) alerts.add(new Alert(WARN, "GITHUB_NOT_LINKED", "sem vínculo com o GitHub"));
        if (github != null) githubAlerts(github, now, alerts);
        if (connector.activeTokens() == 0) {
            alerts.add(new Alert(INFO, "CONNECTOR_MISSING", "sem conector de IA"));
        } else if (connector.lastUploadAt() != null
                && Duration.between(connector.lastUploadAt(), now).compareTo(CONNECTOR_STALE_AFTER) > 0) {
            alerts.add(new Alert(WARN, "CONNECTOR_STALE", "conector sem envio há mais de 3 dias"));
        }
        return alerts;
    }

    private static void githubAlerts(GithubLink github, Instant now, List<Alert> alerts) {
        if (!github.scopes().isEmpty() && !github.scopes().contains(READ_ORG_SCOPE)) {
            alerts.add(new Alert(WARN, "GITHUB_MISSING_READ_ORG", "GitHub sem escopo read:org"));
        }
        Instant expires = github.tokenExpiresAt();
        if (expires == null) return;
        if (expires.isBefore(now)) {
            alerts.add(new Alert(BAD, "GITHUB_TOKEN_EXPIRED", "token do GitHub expirado"));
        } else if (expires.isBefore(now.plus(TOKEN_EXPIRING_WITHIN))) {
            long days = Math.ceilDiv(Duration.between(now, expires).toSeconds(), SECONDS_PER_DAY);
            alerts.add(new Alert(WARN, "GITHUB_TOKEN_EXPIRING", "token do GitHub expira em " + days + " d"));
        }
    }

    private static int alertCount(List<PersonLinks> people) {
        return (int) people.stream().flatMap(p -> p.alerts().stream()).filter(a -> !INFO.equals(a.tone())).count();
    }

    // ── repositories, sync, commits ──

    private Map<String, VcsRepository> repositories(String orgId, List<VcsRepoBinding> bindings) {
        Set<String> ids = bindings.stream().map(VcsRepoBinding::getRepositoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return repositoryRepository.findAllByOrganizationIdAndIdIn(orgId, ids).stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
    }

    private List<RepoLink> repoLinks(String orgId, List<VcsRepoBinding> bindings, Map<String, VcsRepository> repositories) {
        if (bindings.isEmpty()) return List.of();
        Map<String, String> projectNames = projectRepository.findAllActiveByOrganizationId(orgId).stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
        return bindings.stream().filter(b -> b.getRepositoryId() != null)
                .map(b -> {
                    VcsRepository repo = repositories.get(b.getRepositoryId());
                    String name = repo != null && repo.getDisplayName() != null ? repo.getDisplayName() : b.getRepositoryId();
                    return new RepoLink(b.getRepositoryId(), name, b.getProvider() != null ? b.getProvider().name() : null,
                            b.getProjectId(), projectNames.get(b.getProjectId()), b.isSyncEnabled(), b.getLastSyncAt());
                })
                .sorted(Comparator.comparing(RepoLink::repo, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<IntegrationSyncLog> syncLogs(String orgId, Instant now) {
        Instant from = SyncRunsSummary.windowStart(SprintWindow.toDay(now));
        return Stream.of(Integration.AZURE_DEVOPS, Integration.GITHUB)
                .flatMap(i -> syncLogRepository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(orgId, i,
                        SyncRunsSummary.LEAF_OPERATIONS, from).stream())
                .toList();
    }

    private UnlinkedCommits unlinkedCommits(String orgId, Set<String> repositoryIds, Instant now) {
        List<VcsCommit> commits = repositoryIds.isEmpty() ? List.of()
                : commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(orgId, repositoryIds,
                        now.minus(Duration.ofDays(UNLINKED_WINDOW_DAYS)), now);
        List<VcsCommit> unlinked = commits.stream().filter(c -> c.getAuthorUserId() == null).toList();
        Map<Optional<String>, Long> byLogin = unlinked.stream()
                .collect(Collectors.groupingBy(c -> Optional.ofNullable(c.getAuthorLogin()), Collectors.counting()));
        List<UnlinkedLogin> top = byLogin.entrySet().stream()
                .map(e -> new UnlinkedLogin(e.getKey().orElse(null),
                        e.getKey().map(l -> l.endsWith(BOT_SUFFIX)).orElse(false), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(UnlinkedLogin::commits).reversed()
                        .thenComparing(UnlinkedLogin::login, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)))
                .limit(TOP_UNLINKED_LOGINS)
                .toList();
        return new UnlinkedCommits(commits.size(), unlinked.size(), Ratios.percent(unlinked.size(), commits.size()), top);
    }

    private static String nameOf(Map<String, User> users, String userId) {
        if (userId == null) return null;
        User user = users.get(userId);
        return user != null ? displayName(user) : null;
    }

    /** Name, else the user id — never the login e-mail. */
    private static String displayName(User user) {
        return present(user.getName()) ? user.getName() : user.getId();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
