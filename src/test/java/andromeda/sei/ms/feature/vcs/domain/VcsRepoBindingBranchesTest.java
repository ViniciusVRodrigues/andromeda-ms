package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchesInvalidException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VcsRepoBindingBranchesTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private static VcsRepoBinding binding() {
        return new VcsRepoBinding("b1", "org", "p1", "r1", VcsProvider.GITHUB, true);
    }

    @Test
    void should_followDefaultBranch_when_legacyBinding() {
        VcsRepoBinding b = binding();

        assertThat(b.effectiveBranches("develop")).containsExactly("develop");
        assertThat(b.effectiveProductionBranch("develop")).isEqualTo("develop");
        assertThat(b.isBranchesConfigured()).isFalse();
        assertThat(b.isFilterPullRequestsByBranch()).isFalse();
        assertThat(b.getMissingBranches()).isEmpty();
    }

    @Test
    void should_treatEmptyListAsLegacy() {
        VcsRepoBinding b = binding();
        b.setBranches(List.of());
        b.setProductionBranch("");

        assertThat(b.effectiveBranches("develop")).containsExactly("develop");
        assertThat(b.effectiveProductionBranch("develop")).isEqualTo("develop");
        assertThat(b.isBranchesConfigured()).isFalse();
    }

    @Test
    void should_returnNoBranch_when_legacyAndRepositoryHasNoDefault() {
        assertThat(binding().effectiveBranches(null)).isEmpty();
        assertThat(binding().effectiveProductionBranch(null)).isNull();
    }

    @Test
    void should_useStoredBranchesAndProduction() {
        VcsRepoBinding b = binding();
        b.setBranches(List.of("develop", "main"));
        b.setProductionBranch("main");

        assertThat(b.effectiveBranches("trunk")).containsExactly("develop", "main");
        assertThat(b.effectiveProductionBranch("trunk")).isEqualTo("main");
        assertThat(b.isBranchesConfigured()).isTrue();
    }

    @Test
    void should_deriveSyncState_when_notStored() {
        VcsRepoBinding b = binding();
        assertThat(b.effectiveSyncState(NOW)).isEqualTo(VcsSyncState.PENDING);
        b.setLastSyncAt(NOW.minusSeconds(5));
        assertThat(b.effectiveSyncState(NOW)).isEqualTo(VcsSyncState.OK);
    }

    @Test
    void should_returnFailed_when_runningForMoreThan30Minutes() {
        VcsRepoBinding b = binding();
        b.setSyncState(VcsSyncState.RUNNING);
        b.setSyncStateAt(NOW.minus(Duration.ofMinutes(31)));

        assertThat(b.effectiveSyncState(NOW)).isEqualTo(VcsSyncState.FAILED);
        assertThat(b.isSyncRunning(NOW)).isFalse();
    }

    @Test
    void should_stayRunning_when_recent() {
        VcsRepoBinding b = binding();
        b.setSyncState(VcsSyncState.RUNNING);
        b.setSyncStateAt(NOW.minus(Duration.ofMinutes(5)));

        assertThat(b.effectiveSyncState(NOW)).isEqualTo(VcsSyncState.RUNNING);
        assertThat(b.isSyncRunning(NOW)).isTrue();
    }

    @Test
    void should_compareSetsWithoutOrder_andAgainstEffective() {
        VcsRepoBinding b = binding();

        assertThat(b.sameBranchSet(List.of("develop"), "develop")).isTrue();
        assertThat(b.sameBranchSet(List.of("develop", "main"), "develop")).isFalse();
        b.setBranches(List.of("develop", "main"));
        assertThat(b.sameBranchSet(List.of("main", "develop"), "x")).isTrue();
    }

    @Test
    void should_acceptValidSelection() {
        VcsRepoBinding.validateBranches(List.of("a", "b"), "b");
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < VcsRepoBinding.MAX_BRANCHES; i++) ten.add("b" + i);
        VcsRepoBinding.validateBranches(ten, "b0");
    }

    @Test
    void should_rejectInvalidSelections() {
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i <= VcsRepoBinding.MAX_BRANCHES; i++) eleven.add("b" + i);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(eleven, "b0"))
                .isInstanceOf(RepoBranchesInvalidException.class);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(List.of(), "a"))
                .isInstanceOf(RepoBranchesInvalidException.class);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(List.of("a", "a"), "a"))
                .isInstanceOf(RepoBranchesInvalidException.class);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(List.of("a", " "), "a"))
                .isInstanceOf(RepoBranchesInvalidException.class);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(List.of("a"), null))
                .isInstanceOf(RepoBranchesInvalidException.class);
        assertThatThrownBy(() -> VcsRepoBinding.validateBranches(List.of("a"), "b"))
                .isInstanceOf(RepoBranchesInvalidException.class)
                .extracting("code").isEqualTo("REPO_BRANCHES_INVALID");
    }
}
