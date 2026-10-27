package andromeda.sei.ms.feature.vcs.infrastructure.migration;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.usecase.ReattributeVcsAuthorshipUseCase;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepositoryDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VcsAuthorshipBackfillMigrationTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private UserRepository userRepository;
    @Mock private UserProviderAccountRepository accountRepository;
    @Mock private ReattributeVcsAuthorshipUseCase useCase;

    private VcsAuthorshipBackfillMigration migration;

    @BeforeEach
    void setUp() {
        migration = new VcsAuthorshipBackfillMigration(mongoTemplate, userRepository, accountRepository, useCase);
    }

    private void givenOrganizations(String... ids) {
        when(mongoTemplate.findDistinct(any(Query.class), eq("organizationId"), eq(VcsRepositoryDocument.class),
                eq(String.class))).thenReturn(List.of(ids));
    }

    private static User user(String id, String githubId, String azureId, boolean active) {
        return new User(id, "org-1", id + "@x.com", null, "N", "r", null, githubId, azureId, null, null, true, active,
                0L, Instant.now(), Instant.now());
    }

    private static UserProviderAccount githubAccount(String userId) {
        Instant now = Instant.now();
        return new UserProviderAccount("a-" + userId, "org-1", userId, Provider.GITHUB, "1", "login", null, "enc",
                null, null, List.of(), now, true, 0L, now, now);
    }

    @Test
    void should_runOnlyForActiveUsersWithALinkedIdentity() {
        givenOrganizations("org-1");
        when(accountRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(githubAccount("u1")));
        when(userRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(
                user("u1", null, null, true), user("u2", "77", null, true), user("u3", null, "az-1", true),
                user("u4", null, null, true), user("u5", "88", null, false)));

        migration.run(null);

        verify(useCase).execute("org-1", "u1");
        verify(useCase).execute("org-1", "u2");
        verify(useCase).execute("org-1", "u3");
        verify(useCase, never()).execute("org-1", "u4");
        verify(useCase, never()).execute("org-1", "u5");
    }

    @Test
    void should_keepGoing_whenOneUserOrOrganizationFails() {
        givenOrganizations("org-1", "org-2");
        when(accountRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenThrow(new IllegalStateException("x"));
        when(accountRepository.findAllByOrganizationIdAndActiveTrue("org-2")).thenReturn(List.of());
        when(userRepository.findAllByOrganizationId("org-2")).thenReturn(List.of(
                user("u1", "1", null, true), user("u2", "2", null, true)));
        doThrow(new IllegalStateException("boom")).when(useCase).execute("org-2", "u1");

        migration.run(null);

        verify(useCase).execute("org-2", "u2");
    }

    @Test
    void should_doNothing_whenNoOrganizationHasVcsData() {
        givenOrganizations();

        migration.run(null);

        verify(useCase, never()).execute(any(), any());
    }
}
