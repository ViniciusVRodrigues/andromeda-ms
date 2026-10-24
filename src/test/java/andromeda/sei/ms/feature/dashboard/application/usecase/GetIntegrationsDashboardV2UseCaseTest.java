package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.PersonLinks;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncDay;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.12 — integrations v2, table tests ported from {@code mock/integrations.js}:
 * connection states, people × links with alerts, sync runs (leaf operations only), the latest
 * problems and unlinked commits by login. Now = Sunday 2026-10-11 12:00Z.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetIntegrationsDashboardV2UseCaseTest {

    static final String ORG = "org-1";
    static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    static final String PAT = "SECRET-PAT-0001";
    static final String ACCESS = "SECRET-ACCESS-0002";
    static final String REFRESH = "SECRET-REFRESH-0003";
    static final String GH_PAT = "SECRET-GHPAT-0004";
    static final String TOKEN_HASH = "SECRET-HASH-0005";
    static final String ENC_ACCESS = "SECRET-ENCACCESS-0006";
    static final String ENC_REFRESH = "SECRET-ENCREFRESH-0007";
    static final String PASSWORD_HASH = "SECRET-PWHASH-0008";
    static final String INVITE = "SECRET-INVITE-0009";
    static final String GH_EMAIL = "ana.github@example.com";

    @Mock UserRepository userRepository;
    @Mock UserProviderAccountRepository providerAccountRepository;
    @Mock AzureOrgUserRepository azureOrgUserRepository;
    @Mock ConnectorTokenRepository connectorTokenRepository;
    @Mock AIUsageRepository aiUsageRepository;
    @Mock AzureConnectionRepository azureConnectionRepository;
    @Mock GithubConnectionRepository githubConnectionRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock IntegrationSyncLogRepository syncLogRepository;
    @Mock VcsRepoBindingRepository bindingRepository;
    @Mock VcsRepositoryRepository repositoryRepository;
    @Mock VcsCommitRepository commitRepository;
    @Mock ProjectRepository projectRepository;

    final List<IntegrationSyncLog> azureLogs = new ArrayList<>();
    final List<IntegrationSyncLog> githubLogs = new ArrayList<>();
    final List<VcsCommit> commits = new ArrayList<>();
    GetIntegrationsDashboardV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = useCase(this);
        seed(this);
    }

    static GetIntegrationsDashboardV2UseCase useCase(GetIntegrationsDashboardV2UseCaseTest t) {
        return new GetIntegrationsDashboardV2UseCase(t.userRepository, t.providerAccountRepository,
                t.azureOrgUserRepository, t.connectorTokenRepository, t.aiUsageRepository,
                t.azureConnectionRepository, t.githubConnectionRepository, t.organizationRepository,
                t.syncLogRepository, t.bindingRepository, t.repositoryRepository, t.commitRepository,
                t.projectRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Ana: everything linked; Bruno: nothing; Carla: GitHub without read:org, expiring token, stale connector. */
    static void seed(GetIntegrationsDashboardV2UseCaseTest t) {
        when(t.userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                user("u-ana", "Ana", "gh-ana", "az-ana", "ana@azure.example.com", true),
                user("u-bruno", "Bruno", null, null, null, true),
                user("u-carla", "Carla", "gh-carla", "az-carla", null, true),
                user("u-gone", "Gone", null, null, null, false)));
        when(t.providerAccountRepository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of(
                account("u-ana", "ana-gh", List.of("repo", "read:org"), NOW.plus(Duration.ofDays(90))),
                account("u-carla", "carla-gh", List.of("repo"), NOW.plus(Duration.ofDays(3)))));
        when(t.azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of(
                new andromeda.sei.ms.feature.azure.domain.AzureOrgUser("x", ORG, "az-carla", "Carla",
                        "carla@azure.example.com", "carla@azure.example.com", java.util.Set.of(andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource.TEAM), true, NOW, NOW, 0L)));
        when(t.connectorTokenRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                token("t1", "u-ana", "notebook", true), token("t2", "u-ana", "antigo", false),
                token("t3", "u-carla", "desktop", true)));
        when(t.aiUsageRepository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(eq(ORG), anyCollection(), any(), any())).thenReturn(List.of(
                usage("u-ana", NOW.minus(Duration.ofHours(5))), usage("u-carla", NOW.minus(Duration.ofDays(5)))));
        AzureConnection azure = new AzureConnection("c1", ORG, "https://dev.azure.com/orbital", ACCESS, REFRESH,
                NOW.plus(Duration.ofMinutes(47)), "u-ana", AzureConnectionStatus.CONNECTED,
                NOW.minus(Duration.ofMinutes(9)), 0, "TOKEN_EXPIRED", "Access token expired", true, NOW, NOW, 0L,
                AzureAuthMethod.OAUTH, PAT);
        when(t.azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(azure));
        when(t.githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(
                new GithubConnection("g1", ORG, GH_PAT, "u-carla", GithubConnectionStatus.CONNECTED,
                        NOW.minus(Duration.ofMinutes(14)), 0, null, null, true, NOW, NOW, 0L)));
        when(t.organizationRepository.findById(ORG)).thenReturn(Optional.empty());
        when(t.syncLogRepository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(eq(ORG),
                eq(Integration.AZURE_DEVOPS), any(), any())).thenReturn(t.azureLogs);
        when(t.syncLogRepository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(eq(ORG),
                eq(Integration.GITHUB), any(), any())).thenReturn(t.githubLogs);
        VcsRepoBinding binding = new VcsRepoBinding("b1", ORG, "p1", "r1", VcsProvider.GITHUB, true);
        binding.setLastSyncAt(NOW.minus(Duration.ofMinutes(5)));
        when(t.bindingRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(binding));
        when(t.repositoryRepository.findAllByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(
                new VcsRepository("r1", ORG, VcsProvider.GITHUB, "123", "orbital/api")));
        when(t.commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(eq(ORG), anyCollection(),
                any(), any())).thenReturn(t.commits);
        when(t.projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(
                new Project("p1", ORG, "Orion", "orion", null, true, List.of("u-ana"), 0L, NOW, NOW)));
    }

    @Test
    void should_reportConnectionStates_withoutAnySecret() {
        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        var azure = out.connections().azure();
        assertThat(azure.status()).isEqualTo("CONNECTED");
        assertThat(azure.authMethod()).isEqualTo("OAUTH");
        assertThat(azure.authorizedBy()).isEqualTo("Ana");
        assertThat(azure.lastError().code()).isEqualTo("TOKEN_EXPIRED");
        assertThat(azure.lastError().at()).isNull();
        var github = out.connections().github();
        assertThat(github.authMethod()).isEqualTo("PAT");
        assertThat(github.lastError()).isNull();
        assertThat(github.workItemLink().prefixes()).containsExactly("AB#");
        var ai = out.connections().aiConnector();
        assertThat(ai.activeTokens()).isEqualTo(2);
        assertThat(ai.revokedTokens()).isEqualTo(1);
        assertThat(ai.people()).isEqualTo(2);
        assertThat(ai.lastUploadAt()).isEqualTo(NOW.minus(Duration.ofHours(5)));
        assertThat(out.toString()).doesNotContain(PAT, ACCESS, REFRESH, GH_PAT, TOKEN_HASH, ENC_ACCESS, ENC_REFRESH,
                PASSWORD_HASH, INVITE, GH_EMAIL);
    }

    @Test
    void should_reportDisconnected_when_thereIsNoConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        assertThat(out.connections().azure().status()).isEqualTo("DISCONNECTED");
        assertThat(out.connections().github().status()).isEqualTo("DISCONNECTED");
        // without a connection, a missing link is no alert
        assertThat(person(out, "u-bruno").alerts()).extracting("code").containsExactly("CONNECTOR_MISSING");
    }

    @Test
    void should_listActivePeopleWithTheirLinksAndAlerts() {
        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        assertThat(out.people()).extracting(PersonLinks::userId).containsExactly("u-ana", "u-bruno", "u-carla");
        PersonLinks ana = person(out, "u-ana");
        assertThat(ana.azure().email()).isEqualTo("ana@azure.example.com");
        assertThat(ana.github().login()).isEqualTo("ana-gh");
        assertThat(ana.connector().activeTokens()).isEqualTo(1);
        assertThat(ana.connector().revokedTokens()).isEqualTo(1);
        assertThat(ana.connector().labels()).containsExactly("notebook");
        assertThat(ana.alerts()).isEmpty();
        assertThat(person(out, "u-bruno").alerts()).extracting("code")
                .containsExactly("AZURE_NOT_LINKED", "GITHUB_NOT_LINKED", "CONNECTOR_MISSING");
        PersonLinks carla = person(out, "u-carla");
        assertThat(carla.azure().email()).as("falls back to the Azure org user").isEqualTo("carla@azure.example.com");
        assertThat(carla.alerts()).extracting("code")
                .containsExactly("GITHUB_MISSING_READ_ORG", "GITHUB_TOKEN_EXPIRING", "CONNECTOR_STALE");
        assertThat(carla.alerts().get(1).text()).isEqualTo("token do GitHub expira em 3 d");
        // info alerts do not count
        assertThat(out.alertCount()).isEqualTo(5);
    }

    @Test
    void should_flagAnExpiredGithubToken() {
        when(providerAccountRepository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of(
                account("u-ana", "ana-gh", List.of("repo", "read:org"), NOW.minus(Duration.ofDays(2)))));

        assertThat(person(useCase.execute(ORG), "u-ana").alerts()).extracting("tone", "code")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("bad", "GITHUB_TOKEN_EXPIRED"));
    }

    @Test
    void should_summarizeLeafSyncRunsPerDay_ignoringParentsChildrenAndRunning() {
        azureLogs.add(log(Integration.AZURE_DEVOPS, SyncOperation.SYNC_WORK_ITEMS, SyncStatus.SUCCESS,
                "2026-10-10T08:00:00Z", 10_000L, 100, 0));
        azureLogs.add(log(Integration.AZURE_DEVOPS, SyncOperation.SYNC_SPRINTS, SyncStatus.PARTIAL,
                "2026-10-10T09:00:00Z", 30_000L, 50, 5));
        azureLogs.add(log(Integration.AZURE_DEVOPS, SyncOperation.SYNC_BINDING, SyncStatus.SUCCESS,
                "2026-10-10T08:00:00Z", 99_000L, 999, 0));
        azureLogs.add(log(Integration.AZURE_DEVOPS, SyncOperation.ORGANIZATION_SYNC, SyncStatus.FAILED,
                "2026-10-10T08:00:00Z", 99_000L, 999, 999));
        azureLogs.add(log(Integration.AZURE_DEVOPS, SyncOperation.SYNC_WORK_ITEMS, SyncStatus.RUNNING,
                "2026-10-11T11:00:00Z", null, 0, 0));
        githubLogs.add(log(Integration.GITHUB, SyncOperation.SYNC_REPOS, SyncStatus.FAILED,
                "2026-10-11T10:00:00Z", 60_000L, 40, 40));

        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        assertThat(out.sync().byDay()).hasSize(14);
        SyncDay saturday = out.sync().byDay().get(12);
        assertThat(saturday).isEqualTo(new SyncDay("2026-10-10", "10/10", 20.0, null, 30.0, null, 150, 5));
        SyncDay today = out.sync().byDay().get(13);
        assertThat(today.githubAvgSec()).isEqualTo(60.0);
        assertThat(today.azureAvgSec()).isNull();
        assertThat(out.sync().byDay().get(0).date()).isEqualTo("2026-09-28");
        assertThat(out.sync().triggers()).extracting("integration", "scheduled")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AZURE_DEVOPS", 2),
                        org.assertj.core.groups.Tuple.tuple("GITHUB", 1));
        assertThat(out.sync().problems()).extracting("operation")
                .containsExactly("SYNC_REPOS", "SYNC_SPRINTS");
        assertThat(out.sync().problems().get(0).error().code()).isEqualTo("UPSTREAM");
        assertThat(out.sync().problems().get(0).error().httpStatus()).isEqualTo(502);
        assertThat(out.sync().today().runs()).isEqualTo(1);
        assertThat(out.sync().today().failed()).isEqualTo(1);
        assertThat(out.toString()).doesNotContain("stack detail with url");
    }

    @Test
    void should_countUnlinkedCommitsByLogin_overTheLast30Days() {
        commits.add(commit("c1", "ana-gh", "u-ana"));
        commits.add(commit("c2", "dependabot[bot]", null));
        commits.add(commit("c3", "dependabot[bot]", null));
        commits.add(commit("c4", "stranger", null));
        commits.add(commit("c5", null, null));

        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        var unlinked = out.unlinkedCommits();
        assertThat(unlinked.total()).isEqualTo(5);
        assertThat(unlinked.withoutUser()).isEqualTo(4);
        assertThat(unlinked.pct()).isEqualTo(80.0);
        assertThat(unlinked.byLogin()).extracting("login", "bot", "commits").containsExactly(
                org.assertj.core.groups.Tuple.tuple("dependabot[bot]", true, 2),
                org.assertj.core.groups.Tuple.tuple(null, false, 1),
                org.assertj.core.groups.Tuple.tuple("stranger", false, 1));
    }

    @Test
    void should_answerNullPct_when_thereAreNoCommits() {
        assertThat(useCase.execute(ORG).unlinkedCommits().pct()).isNull();
    }

    @Test
    void should_listBoundRepositoriesWithTheirProject() {
        IntegrationsDashboardV2Output out = useCase.execute(ORG);

        assertThat(out.repos()).singleElement().satisfies(r -> {
            assertThat(r.repo()).isEqualTo("orbital/api");
            assertThat(r.project()).isEqualTo("Orion");
            assertThat(r.syncEnabled()).isTrue();
        });
    }

    static PersonLinks person(IntegrationsDashboardV2Output out, String userId) {
        return out.people().stream().filter(p -> p.userId().equals(userId)).findFirst().orElseThrow();
    }

    static User user(String id, String name, String githubId, String azureId, String azureEmail, boolean active) {
        return new User(id, ORG, id + "@example.com", PASSWORD_HASH, name, "role", null, githubId, azureId,
                azureEmail, INVITE, true, active, 0L, NOW, NOW);
    }

    static UserProviderAccount account(String userId, String login, List<String> scopes, Instant expiresAt) {
        return new UserProviderAccount("a-" + userId, ORG, userId, Provider.GITHUB, "ext-" + userId, login, GH_EMAIL,
                ENC_ACCESS, ENC_REFRESH, expiresAt, scopes, NOW.minus(Duration.ofDays(30)), true, 0L, NOW, NOW);
    }

    static ConnectorToken token(String id, String userId, String label, boolean active) {
        return new ConnectorToken(id, ORG, userId, label, TOKEN_HASH, active, 0L, NOW, NOW);
    }

    static AIUsage usage(String userId, Instant uploadedAt) {
        return new AIUsage("au-" + userId, ORG, userId, null, "ds", LocalDate.of(2026, 10, 10), "gpt", null, null,
                null, null, 10, 10, 1, "vscode", null, BigDecimal.ONE, uploadedAt, true, 0L, NOW, NOW);
    }

    static IntegrationSyncLog log(Integration integration, SyncOperation operation, SyncStatus status, String startedAt,
                                  Long durationMs, int processed, int failed) {
        IntegrationSyncLog log = new IntegrationSyncLog("l-" + operation + startedAt, ORG, integration, operation,
                status, SyncTrigger.SCHEDULED, "p1", Instant.parse(startedAt), null, durationMs, processed, failed,
                status == SyncStatus.SUCCESS ? null : "UPSTREAM", "upstream failed");
        log.setHttpStatus(status == SyncStatus.SUCCESS ? null : 502);
        log.setErrorDetail("stack detail with url");
        return log;
    }

    static VcsCommit commit(String id, String login, String userId) {
        VcsCommit commit = new VcsCommit(id, ORG, "r1", VcsProvider.GITHUB, "sha-" + id);
        commit.setAuthorLogin(login);
        commit.setAuthorUserId(userId);
        commit.setAuthoredAt(NOW.minus(Duration.ofDays(2)));
        return commit;
    }
}
