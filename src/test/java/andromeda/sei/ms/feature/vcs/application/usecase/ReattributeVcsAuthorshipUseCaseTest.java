package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.domain.VcsAttribution;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReattributeVcsAuthorshipUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final String AZURE_GUID = "1b2c3d4e-0000-4000-8000-123456789abc";

    @Mock private VcsProviderRegistry registry;
    @Mock private VcsAuthorResolver github;
    @Mock private VcsAuthorResolver azure;
    @Mock private UserRepository userRepository;
    @Mock private UserProviderAccountRepository accountRepository;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private VcsPullRequestRepository pullRequestRepository;
    @Mock private VcsReviewEventRepository reviewEventRepository;
    @Mock private CalculatePullRequestMetricsUseCase metrics;

    private ReattributeVcsAuthorshipUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReattributeVcsAuthorshipUseCase(registry, userRepository, accountRepository, commitRepository,
                pullRequestRepository, reviewEventRepository, metrics);
        lenient().when(registry.authorResolver(VcsProvider.GITHUB)).thenReturn(github);
        lenient().when(registry.authorResolver(VcsProvider.AZURE_REPOS)).thenReturn(azure);
        lenient().when(pullRequestRepository.attributeByAuthorExternalIds(any(), any(), any(), any()))
                .thenReturn(VcsAttribution.none());
        lenient().when(reviewEventRepository.attributeByActorExternalIds(any(), any(), any(), any()))
                .thenReturn(VcsAttribution.none());
    }

    private static User user(String githubId, String azureId, String azureEmail, String email) {
        return new User(USER, ORG, email, null, "Ana", "role", null, githubId, azureId, azureEmail, null, true,
                true, 0L, Instant.now(), Instant.now());
    }

    private static UserProviderAccount account(String externalId, String login) {
        Instant now = Instant.now();
        return new UserProviderAccount("acc", ORG, USER, Provider.GITHUB, externalId, login, null, "enc", null, null,
                List.of(), now, true, 0L, now, now);
    }

    private void givenUser(User user) {
        when(userRepository.findByIdAndOrganizationId(USER, ORG)).thenReturn(Optional.of(user));
    }

    private void givenGithubAccount(UserProviderAccount account) {
        when(accountRepository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(ORG, USER, Provider.GITHUB))
                .thenReturn(Optional.ofNullable(account));
    }

    private static Collection<String> containing(String... values) {
        return argThat(c -> c != null && c.containsAll(List.of(values)));
    }

    @Test
    void should_attributeGithubHistoryById_andOldCommitsByLogin_andRecalculateAffectedRepositories() {
        givenUser(user(null, null, null, "ana@x.com"));
        givenGithubAccount(account("58421", "Octo-Cat"));
        when(github.resolveUserId(ORG, "58421")).thenReturn(USER);
        when(commitRepository.attributeByAuthorExternalIds(eq(ORG), eq(VcsProvider.GITHUB), containing("58421"),
                eq(USER))).thenReturn(66L);
        when(pullRequestRepository.attributeByAuthorExternalIds(eq(ORG), eq(VcsProvider.GITHUB), containing("58421"),
                eq(USER))).thenReturn(new VcsAttribution(10, Set.of("repo-1")));
        when(reviewEventRepository.attributeByActorExternalIds(eq(ORG), eq(VcsProvider.GITHUB), containing("58421"),
                eq(USER))).thenReturn(new VcsAttribution(3, Set.of("repo-1", "repo-2")));

        useCase.execute(ORG, USER);

        verify(commitRepository).attributeByLegacyAuthorLogin(ORG, VcsProvider.GITHUB, "Octo-Cat", USER);
        verify(metrics).execute(ORG, "repo-1");
        verify(metrics).execute(ORG, "repo-2");
        verify(metrics, never()).execute(eq(ORG), eq("repo-3"));
    }

    @Test
    void should_notRecalculateMetrics_whenNoPullRequestOrReviewChanged() {
        givenUser(user(null, null, null, "ana@x.com"));
        givenGithubAccount(account("58421", "octocat"));
        when(github.resolveUserId(ORG, "58421")).thenReturn(USER);

        useCase.execute(ORG, USER);

        verifyNoInteractions(metrics);
    }

    @Test
    void should_useLegacyGithubId_whenResolverMapsItToTheUser() {
        givenUser(user("777", null, null, "ana@x.com"));
        givenGithubAccount(null);
        when(github.resolveUserId(ORG, "777")).thenReturn(USER);

        useCase.execute(ORG, USER);

        verify(commitRepository).attributeByAuthorExternalIds(eq(ORG), eq(VcsProvider.GITHUB), containing("777"),
                eq(USER));
        verify(commitRepository, never()).attributeByLegacyAuthorLogin(any(), any(), any(), any());
    }

    @Test
    void should_discardGithubKeys_whenResolverMapsThemToSomeoneElse_andNotMatchLogin() {
        givenUser(user(null, null, null, "ana@x.com"));
        givenGithubAccount(account("58421", "octocat"));
        when(github.resolveUserId(ORG, "58421")).thenReturn("another-user");

        useCase.execute(ORG, USER);

        verify(commitRepository, never()).attributeByAuthorExternalIds(any(), any(), any(), any());
        verify(commitRepository, never()).attributeByLegacyAuthorLogin(any(), any(), any(), any());
        verify(pullRequestRepository, never()).attributeByAuthorExternalIds(any(), any(), any(), any());
    }

    @Test
    void should_attributeAzureByIdAndEmail_withRawAndNormalizedIds() {
        String raw = AZURE_GUID.replace("-", "").toUpperCase();
        givenUser(user(null, raw, "ana@az.com", "ana@x.com"));
        givenGithubAccount(null);
        when(azure.resolveUserId(ORG, raw)).thenReturn(USER);
        when(azure.resolveUserId(ORG, AZURE_GUID)).thenReturn(USER);
        when(azure.resolveUserId(ORG, null, "ana@az.com")).thenReturn(USER);
        when(azure.resolveUserId(ORG, null, "ana@x.com")).thenReturn(USER);

        useCase.execute(ORG, USER);

        verify(commitRepository).attributeByAuthorEmails(eq(ORG), eq(VcsProvider.AZURE_REPOS),
                containing("ana@az.com", "ana@x.com"), eq(USER));
        verify(pullRequestRepository).attributeByAuthorExternalIds(eq(ORG), eq(VcsProvider.AZURE_REPOS),
                containing(raw, AZURE_GUID), eq(USER));
        verify(reviewEventRepository).attributeByActorExternalIds(eq(ORG), eq(VcsProvider.AZURE_REPOS),
                containing(raw, AZURE_GUID), eq(USER));
    }

    @Test
    void should_ignoreAmbiguousAzureEmail_whenItResolvesToAnotherUser() {
        givenUser(user(null, null, null, "shared@x.com"));
        givenGithubAccount(null);
        when(azure.resolveUserId(ORG, null, "shared@x.com")).thenReturn("another-user");

        useCase.execute(ORG, USER);

        verify(commitRepository, never()).attributeByAuthorEmails(any(), any(), any(), any());
    }

    @Test
    void should_doNothing_whenPersonHasNoIdentity() {
        givenUser(user(null, null, null, null));
        givenGithubAccount(null);

        useCase.execute(ORG, USER);

        verifyNoInteractions(commitRepository, pullRequestRepository, reviewEventRepository, metrics);
    }

    @Test
    void should_doNothing_whenUserDoesNotExist() {
        when(userRepository.findByIdAndOrganizationId(USER, ORG)).thenReturn(Optional.empty());

        useCase.execute(ORG, USER);

        verifyNoInteractions(commitRepository, pullRequestRepository, reviewEventRepository, metrics);
        verify(accountRepository, never()).findByOrganizationIdAndUserIdAndProviderAndActiveTrue(anyString(),
                anyString(), any());
    }
}
