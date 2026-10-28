package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.sync.application.SyncCooldownService;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.domain.exception.SyncQueueFullException;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRepositoryDataWiper;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** branches-por-vinculo 2.11: the PATCH and its destructive rules. */
@ExtendWith(MockitoExtension.class)
class UpdateRepositoryBindingUseCaseTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsProviderClient client;
    @Mock private VcsRepositoryDataWiper dataWiper;
    @Mock private SyncOrchestrator syncOrchestrator;
    @Mock private SyncCooldownService cooldownService;

    private UpdateRepositoryBindingUseCase useCase;
    private VcsRepository repository;
    private VcsRepoBinding binding;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        useCase = new UpdateRepositoryBindingUseCase(bindingRepository, repositoryRepository, organizationRepository,
                new BindingBranchSettings(providerRegistry, bindingRepository, dataWiper, syncOrchestrator, cooldownService));
        repository = new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello");
        repository.setDefaultBranch("develop");
        binding = new VcsRepoBinding("b-1", ORG, "proj-1", "repo-1", VcsProvider.GITHUB, true);
        binding.setLastSyncAt(Instant.parse("2026-10-01T00:00:00Z"));
        lenient().when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-1", ORG)).thenReturn(Optional.of(binding));
        lenient().when(repositoryRepository.findByIdAndOrganizationId("repo-1", ORG)).thenReturn(Optional.of(repository));
        lenient().when(bindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
        lenient().when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(CREDENTIALS));
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
        lenient().when(client.listBranches(any(), any())).thenReturn(List.of(new VcsBranch("develop", true),
                new VcsBranch("main", false), new VcsBranch("release", false)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static BindingBranchSettings.Change change(List<String> branches, String production, Boolean filter) {
        return new BindingBranchSettings.Change(null, branches, production, filter);
    }

    @Test
    void should_wipeOnlyThisRepository_resetTheBinding_andSubmitTheSync_whenTheSetChanges() {
        RepoBindingOutput out = useCase.execute("b-1", change(List.of("develop", "main"), "main", false));

        var order = inOrder(bindingRepository, dataWiper, cooldownService, syncOrchestrator);
        order.verify(dataWiper).wipeRepository(ORG, "repo-1");
        order.verify(bindingRepository).save(binding);
        order.verify(cooldownService).release("b-1");
        verify(bindingRepository, times(1)).save(any());
        order.verify(syncOrchestrator).submit(any(SyncRequest.class));
        assertThat(out.resynced()).isTrue();
        assertThat(out.syncState()).isEqualTo(VcsSyncState.PENDING);
        assertThat(binding.getLastSyncAt()).isNull();
        assertThat(binding.getMissingBranches()).isEmpty();
        assertThat(out.branches()).containsExactly("develop", "main");
    }

    @Test
    void should_deleteNothing_when_aLegacyBindingGetsItsDefaultBranchAsTheOnlyBranch() {
        RepoBindingOutput out = useCase.execute("b-1", change(List.of("develop"), "develop", null));

        verify(dataWiper, never()).wipeRepository(any(), any());
        verify(syncOrchestrator, never()).submit(any());
        assertThat(out.resynced()).isFalse();
        assertThat(binding.getLastSyncAt()).isNotNull();
        assertThat(out.branchesConfigured()).isTrue();
    }

    @Test
    void should_compareTheSetWithoutOrder() {
        binding.setBranches(List.of("develop", "main"));
        binding.setProductionBranch("main");

        useCase.execute("b-1", change(List.of("main", "develop"), "main", null));

        verify(dataWiper, never()).wipeRepository(any(), any());
    }

    @Test
    void should_wipe_whenOnlyTheFilterChanges() {
        RepoBindingOutput out = useCase.execute("b-1", new BindingBranchSettings.Change(null, null, null, true));

        verify(dataWiper).wipeRepository(ORG, "repo-1");
        assertThat(out.resynced()).isTrue();
        assertThat(out.filterPullRequestsByBranch()).isTrue();
    }

    @Test
    void should_notDeleteWhileRunning_andWriteNothing() {
        binding.setBranches(List.of("develop"));
        binding.setProductionBranch("develop");
        binding.setSyncState(VcsSyncState.RUNNING);
        binding.setSyncStateAt(Instant.now());

        assertThatThrownBy(() -> useCase.execute("b-1", change(List.of("develop", "main"), "main", null)))
                .hasFieldOrPropertyWithValue("code", "REPO_SYNC_RUNNING");

        verify(dataWiper, never()).wipeRepository(any(), any());
        verify(bindingRepository, never()).save(any());
        assertThat(binding.getBranches()).containsExactly("develop");
    }

    @Test
    void should_proceed_whenTheRunningStateExpired() {
        binding.setSyncState(VcsSyncState.RUNNING);
        binding.setSyncStateAt(Instant.now().minusSeconds(31 * 60));

        useCase.execute("b-1", change(List.of("develop", "main"), "main", null));

        verify(dataWiper).wipeRepository(ORG, "repo-1");
    }

    @Test
    void should_onlySaveAndNotResync_whenOnlyTheProductionBranchChanges() {
        binding.setBranches(List.of("develop", "main"));
        binding.setProductionBranch("develop");
        binding.setSyncState(VcsSyncState.RUNNING);
        binding.setSyncStateAt(Instant.now());

        RepoBindingOutput out = useCase.execute("b-1", change(null, "main", null));

        assertThat(out.productionBranch()).isEqualTo("main");
        assertThat(out.resynced()).isFalse();
        verify(dataWiper, never()).wipeRepository(any(), any());
        verify(syncOrchestrator, never()).submit(any());
        verify(bindingRepository).save(binding);
    }

    @Test
    void should_answer422_whenTheProductionBranchIsNotAmongTheBranches() {
        binding.setBranches(List.of("develop", "main"));
        binding.setProductionBranch("develop");

        assertThatThrownBy(() -> useCase.execute("b-1", change(null, "release", null)))
                .hasFieldOrPropertyWithValue("code", "REPO_BRANCHES_INVALID");
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void should_answer422_whenABranchIsNotInTheProvider_andDeleteNothing() {
        assertThatThrownBy(() -> useCase.execute("b-1", change(List.of("develop", "ghost"), "develop", null)))
                .hasFieldOrPropertyWithValue("code", "REPO_BRANCH_NOT_FOUND");
        verify(dataWiper, never()).wipeRepository(any(), any());
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void should_notBlockOnABranchDeletedRemotely_butStillCheckTheOnesBeingAdded() {
        binding.setBranches(List.of("develop", "release/x"));
        binding.setProductionBranch("develop");
        binding.setMissingBranches(List.of("release/x"));

        RepoBindingOutput out = useCase.execute("b-1", change(List.of("develop", "release/x", "main"), "develop", null));

        assertThat(out.branches()).containsExactly("develop", "release/x", "main");

        binding.setBranches(List.of("develop", "release/x"));
        assertThatThrownBy(() -> useCase.execute("b-1", change(List.of("develop", "ghost"), "develop", null)))
                .hasFieldOrPropertyWithValue("code", "REPO_BRANCH_NOT_FOUND");
    }

    @Test
    void should_notSubmit_whenSyncIsDisabled_butStillWipeAndGoPending() {
        binding.setSyncEnabled(false);

        RepoBindingOutput out = useCase.execute("b-1", change(List.of("develop", "main"), "main", null));

        verify(dataWiper).wipeRepository(ORG, "repo-1");
        verify(syncOrchestrator, never()).submit(any());
        assertThat(out.syncState()).isEqualTo(VcsSyncState.PENDING);
    }

    @Test
    void should_keepTheResult_whenTheSubmitFails() {
        when(syncOrchestrator.submit(any())).thenThrow(new SyncQueueFullException());

        RepoBindingOutput out = useCase.execute("b-1", change(List.of("develop", "main"), "main", null));

        assertThat(out.resynced()).isTrue();
        assertThat(out.syncState()).isEqualTo(VcsSyncState.PENDING);
    }

    @Test
    void should_toggleSyncAlone_withoutReadingTheProviderOrDeleting() {
        RepoBindingOutput out = useCase.execute("b-1", new BindingBranchSettings.Change(false, null, null, null));

        assertThat(out.syncEnabled()).isFalse();
        verify(client, never()).listBranches(any(), any());
        verify(dataWiper, never()).wipeRepository(any(), any());
    }

    @Test
    void should_answer404_forABindingOfAnotherOrganization() {
        TenantContextHolder.set("org-2");
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-1", "org-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("b-1", change(List.of("develop"), "develop", null)))
                .isInstanceOf(RepoBindingNotFoundException.class);
        verify(dataWiper, never()).wipeRepository(any(), any());
    }
}
