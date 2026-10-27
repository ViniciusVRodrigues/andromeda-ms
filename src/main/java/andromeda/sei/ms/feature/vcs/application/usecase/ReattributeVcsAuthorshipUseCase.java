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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Retroactive author attribution (atribuir-historico-vcs D2/D3): once a provider identity points to
 * a user, gives them the commits, pull requests and review events synced earlier without an owner.
 *
 * <p>The candidate keys of the person are each validated by the provider's own
 * {@link VcsAuthorResolver} — a key that resolves to someone else (a shared email) or to nobody is
 * dropped. Only ownerless rows are touched (the repositories filter by organization, provider and a
 * null owner), so an existing owner is never replaced and nothing is ever unassigned. Idempotent.
 * Logs ids and counts only, never an email or login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReattributeVcsAuthorshipUseCase {

    private static final Pattern BARE_GUID = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final VcsProviderRegistry providerRegistry;
    private final UserRepository userRepository;
    private final UserProviderAccountRepository accountRepository;
    private final VcsCommitRepository commitRepository;
    private final VcsPullRequestRepository pullRequestRepository;
    private final VcsReviewEventRepository reviewEventRepository;
    private final CalculatePullRequestMetricsUseCase metricsUseCase;

    public void execute(String organizationId, String userId) {
        Optional<User> found = userRepository.findByIdAndOrganizationId(userId, organizationId);
        if (found.isEmpty()) return;
        User user = found.get();

        Set<String> affectedRepositories = new LinkedHashSet<>();
        long commits = reattributeGithub(organizationId, user, affectedRepositories);
        commits += reattributeAzure(organizationId, user, affectedRepositories);

        affectedRepositories.forEach(repositoryId -> metricsUseCase.execute(organizationId, repositoryId));
        if (commits > 0 || !affectedRepositories.isEmpty()) {
            log.info("VCS history attributed: org={} userId={} commits={} repositoriesWithPullRequestChanges={}",
                    organizationId, userId, commits, affectedRepositories.size());
        }
    }

    // ------------------------------------------------------------------
    // GitHub
    // ------------------------------------------------------------------

    private long reattributeGithub(String organizationId, User user, Set<String> affectedRepositories) {
        VcsAuthorResolver resolver = providerRegistry.authorResolver(VcsProvider.GITHUB);
        Optional<UserProviderAccount> account = accountRepository
                .findByOrganizationIdAndUserIdAndProviderAndActiveTrue(organizationId, user.getId(), Provider.GITHUB);

        Set<String> ids = new LinkedHashSet<>();
        String login = null;
        if (account.isPresent()) {
            String externalId = clean(account.get().getExternalUserId());
            if (externalId != null && resolvesTo(resolver, organizationId, externalId, user)) {
                ids.add(externalId);
                login = clean(account.get().getExternalLogin());
            }
        }
        String githubId = clean(user.getGithubId());
        if (githubId != null && resolvesTo(resolver, organizationId, githubId, user)) ids.add(githubId);

        long commits = 0;
        if (!ids.isEmpty()) {
            commits += commitRepository.attributeByAuthorExternalIds(organizationId, VcsProvider.GITHUB, ids,
                    user.getId());
        }
        if (login != null) {
            commits += commitRepository.attributeByLegacyAuthorLogin(organizationId, VcsProvider.GITHUB, login,
                    user.getId());
        }
        attributePullRequestsAndReviews(organizationId, VcsProvider.GITHUB, ids, user, affectedRepositories);
        return commits;
    }

    // ------------------------------------------------------------------
    // Azure Repos
    // ------------------------------------------------------------------

    private long reattributeAzure(String organizationId, User user, Set<String> affectedRepositories) {
        VcsAuthorResolver resolver = providerRegistry.authorResolver(VcsProvider.AZURE_REPOS);

        Set<String> ids = new LinkedHashSet<>();
        for (String form : azureIdForms(user.getAzureId())) {
            if (resolvesTo(resolver, organizationId, form, user)) ids.add(form);
        }
        Set<String> emails = new LinkedHashSet<>();
        for (String email : new String[] {clean(user.getAzureEmail()), clean(user.getEmail())}) {
            if (email != null && email.contains("@")
                    && user.getId().equals(resolver.resolveUserId(organizationId, null, email))) {
                emails.add(email);
            }
        }

        long commits = 0;
        if (!emails.isEmpty()) {
            commits += commitRepository.attributeByAuthorEmails(organizationId, VcsProvider.AZURE_REPOS, emails,
                    user.getId());
        }
        attributePullRequestsAndReviews(organizationId, VcsProvider.AZURE_REPOS, ids, user, affectedRepositories);
        return commits;
    }

    // ------------------------------------------------------------------

    private void attributePullRequestsAndReviews(String organizationId, VcsProvider provider, Set<String> ids,
                                                 User user, Set<String> affectedRepositories) {
        if (ids.isEmpty()) return;
        VcsAttribution pullRequests =
                pullRequestRepository.attributeByAuthorExternalIds(organizationId, provider, ids, user.getId());
        VcsAttribution reviews =
                reviewEventRepository.attributeByActorExternalIds(organizationId, provider, ids, user.getId());
        affectedRepositories.addAll(pullRequests.repositoryIds());
        affectedRepositories.addAll(reviews.repositoryIds());
    }

    private static boolean resolvesTo(VcsAuthorResolver resolver, String organizationId, String key, User user) {
        return user.getId().equals(resolver.resolveUserId(organizationId, key));
    }

    /** The id as the user stored it, plus the normalized GUID the sync resolves Azure identities by. */
    private static Set<String> azureIdForms(String azureId) {
        Set<String> forms = new LinkedHashSet<>();
        String raw = clean(azureId);
        if (raw == null) return forms;
        forms.add(raw);
        String bare = raw.startsWith("{") && raw.endsWith("}") ? raw.substring(1, raw.length() - 1) : raw;
        if (BARE_GUID.matcher(bare).matches()) {
            String hex = bare.toLowerCase(Locale.ROOT);
            bare = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-" + hex.substring(20);
        } else {
            bare = bare.toLowerCase(Locale.ROOT).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                    ? bare.toLowerCase(Locale.ROOT) : bare;
        }
        forms.add(bare);
        return forms;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
