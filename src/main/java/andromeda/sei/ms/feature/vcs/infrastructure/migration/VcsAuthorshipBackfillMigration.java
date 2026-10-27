package andromeda.sei.ms.feature.vcs.infrastructure.migration;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.usecase.ReattributeVcsAuthorshipUseCase;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepositoryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time migration (atribuir-historico-vcs D5): attributes the unowned commits, pull requests and
 * review events of every person who already has a linked identity — an active GitHub account, a
 * {@code githubId} or an {@code azureId} — as {@link ReattributeVcsAuthorshipUseCase} does when a
 * link is made.
 *
 * <p>Idempotent by construction (only ownerless rows are touched, so a second run finds nothing).
 * A failure in one organization or person is logged and the startup goes on. Logs ids and counts.
 */
@Slf4j
@Component
@Order(34)
@RequiredArgsConstructor
public class VcsAuthorshipBackfillMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final UserProviderAccountRepository accountRepository;
    private final ReattributeVcsAuthorshipUseCase reattributeUseCase;

    @Override
    public void run(ApplicationArguments args) {
        List<String> organizationIds;
        try {
            organizationIds = mongoTemplate.findDistinct(new Query(), "organizationId", VcsRepositoryDocument.class,
                    String.class);
        } catch (RuntimeException e) {
            log.error("VcsAuthorshipBackfillMigration: could not list organizations: {}", e.getClass().getSimpleName());
            return;
        }
        int total = 0;
        for (String organizationId : organizationIds) {
            try {
                total += migrate(organizationId);
            } catch (RuntimeException e) {
                log.error("VcsAuthorshipBackfillMigration: org={} failed: {}", organizationId,
                        e.getClass().getSimpleName());
            }
        }
        log.info("VcsAuthorshipBackfillMigration: complete, {} user(s) processed in {} organization(s)", total,
                organizationIds.size());
    }

    private int migrate(String organizationId) {
        Set<String> withGithubAccount = new HashSet<>();
        for (UserProviderAccount account : accountRepository.findAllByOrganizationIdAndActiveTrue(organizationId)) {
            if (account.getProvider() == Provider.GITHUB) withGithubAccount.add(account.getUserId());
        }
        int processed = 0;
        int failed = 0;
        for (User user : userRepository.findAllByOrganizationId(organizationId)) {
            if (!user.isActive() || !hasIdentity(user, withGithubAccount)) continue;
            try {
                reattributeUseCase.execute(organizationId, user.getId());
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("VcsAuthorshipBackfillMigration: org={} userId={} failed: {}", organizationId, user.getId(),
                        e.getClass().getSimpleName());
            }
        }
        log.info("VcsAuthorshipBackfillMigration: org={} processed={} failed={}", organizationId, processed, failed);
        return processed;
    }

    private static boolean hasIdentity(User user, Set<String> withGithubAccount) {
        return withGithubAccount.contains(user.getId())
                || (user.getGithubId() != null && !user.getGithubId().isBlank())
                || (user.getAzureId() != null && !user.getAzureId().isBlank());
    }
}
