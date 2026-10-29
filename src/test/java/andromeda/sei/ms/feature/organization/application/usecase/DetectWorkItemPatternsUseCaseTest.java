package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.port.WorkItemPatternSampleSource;
import andromeda.sei.ms.feature.organization.application.port.WorkItemReferenceLookup;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase.Trigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns.Mode;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DetectWorkItemPatternsUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Mock OrganizationRepository organizations;
    @Mock WorkItemPatternSampleSource sample;
    @Mock WorkItemReferenceLookup lookup;

    private Organization organization;
    private Set<String> existing;
    private boolean jiraStyle;
    private DetectWorkItemPatternsUseCase useCase;

    @BeforeEach
    void setUp() {
        organization = new Organization(ORG, "Acme", "acme", null, null, List.of(), null, null, true, 1L, NOW, NOW);
        existing = new HashSet<>();
        jiraStyle = false;
        when(organizations.findById(ORG)).thenAnswer(inv -> Optional.of(organization));
        when(organizations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lookup.activeTitlesByExternalId(eq(ORG), anyCollection())).thenAnswer(inv -> {
            Map<String, String> found = new HashMap<>();
            for (Object id : (Collection<?>) inv.getArgument(1)) {
                if (existing.contains(id)) found.put((String) id, "Title " + id);
            }
            return found;
        });
        when(lookup.hasJiraStyleExternalIds(ORG)).thenAnswer(inv -> jiraStyle);
        useCase = new DetectWorkItemPatternsUseCase(organizations, sample, lookup, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void branches(List<String> list) {
        when(sample.recentBranches(eq(ORG), any(), eq(5000))).thenReturn(list);
    }

    /** 300 numeric branches with existing items plus 100 that match nothing. */
    private List<String> numericSample() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 300).forEach(i -> {
            list.add("feature/" + (1000 + i) + "-login");
            existing.add(String.valueOf(1000 + i));
        });
        IntStream.range(0, 100).forEach(i -> list.add("chore/cleanup-deps-" + i));
        return list;
    }

    private static List<String> presets(WorkItemPatterns p) {
        return p.patterns().stream().map(WorkItemPatterns.Entry::preset).toList();
    }

    private static WorkItemPatterns.Candidate candidate(WorkItemPatterns p, String preset) {
        return p.detection().candidates().stream().filter(c -> preset.equals(c.preset())).findFirst().orElseThrow();
    }

    @Test
    void should_applyNumberNextToAbHash_when_numericBranchesCoverEnoughInDefault() {
        branches(numericSample());

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.mode()).isEqualTo(Mode.DETECTED);
        assertThat(presets(result)).containsExactly("AB_HASH", "NUMBER");
        assertThat(result.detection().applied()).isTrue();
        assertThat(result.detection().branchesScanned()).isEqualTo(400);
        assertThat(result.detection().coverage()).isEqualTo(0.75);
        assertThat(result.detection().ranAt()).isEqualTo(NOW);
        WorkItemPatterns.Candidate number = candidate(result, "NUMBER");
        assertThat(number.coverage()).isEqualTo(0.75);
        assertThat(number.precision()).isEqualTo(1.0);
        assertThat(number.matchedBranches()).isEqualTo(300);
        assertThat(number.resolvedBranches()).isEqualTo(300);
        assertThat(number.examples()).hasSize(3);
        assertThat(number.examples().get(0).title()).isEqualTo("Title " + number.examples().get(0).workItemNumber());
        assertThat(result.patterns().get(1).alsoInText()).isFalse();
        assertThat(result.patterns().get(0).alsoInText()).isTrue();
        verify(organizations).save(organization);
    }

    @Test
    void should_onlyStoreTheCandidates_when_organizationIsCustom() {
        branches(numericSample());
        WorkItemPatterns custom = new WorkItemPatterns(true, Mode.CUSTOM,
                List.of(WorkItemPatterns.Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true)),
                "u1", NOW, "u1", NOW, null);
        organization.setWorkItemPatterns(custom);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.SCHEDULED);

        assertThat(result.mode()).isEqualTo(Mode.CUSTOM);
        assertThat(result.patterns()).isEqualTo(custom.patterns());
        assertThat(result.confirmedBy()).isEqualTo("u1");
        assertThat(result.detection().applied()).isFalse();
        assertThat(candidate(result, "NUMBER").coverage()).isEqualTo(0.75);
    }

    @Test
    void should_applyNothing_when_sampleHasFewerThan20Branches() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 12).forEach(i -> {
            list.add("feature/" + (500 + i) + "-x");
            existing.add(String.valueOf(500 + i));
        });
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.mode()).isEqualTo(Mode.DEFAULT);
        assertThat(presets(result)).containsExactly("AB_HASH");
        assertThat(result.detection().applied()).isFalse();
        assertThat(result.detection().branchesScanned()).isEqualTo(12);
        assertThat(candidate(result, "NUMBER").coverage()).isEqualTo(1.0);
    }

    @Test
    void should_leaveACandidateOut_when_itsPrecisionIsBelow80Percent() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 100).forEach(i -> {
            list.add("feature/" + (2000 + i) + "-x");
            if (i < 70) existing.add(String.valueOf(2000 + i));
        });
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        WorkItemPatterns.Candidate number = candidate(result, "NUMBER");
        assertThat(number.precision()).isEqualTo(0.7);
        assertThat(number.coverage()).isEqualTo(0.7);
        assertThat(presets(result)).containsExactly("AB_HASH");
        assertThat(result.detection().applied()).isFalse();
        assertThat(result.detection().coverage()).isEqualTo(0.0);
    }

    @Test
    void should_leaveACandidateOut_when_itsCoverageGainIsBelowFivePoints() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 60).forEach(i -> {
            list.add("feature/AB#" + (100 + i) + "-x");
            existing.add(String.valueOf(100 + i));
        });
        IntStream.range(0, 3).forEach(i -> {
            list.add("feature/" + (900 + i) + "-y");
            existing.add(String.valueOf(900 + i));
        });
        IntStream.range(0, 37).forEach(i -> list.add("feature/alpha-beta-" + i + "-z"));
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(candidate(result, "NUMBER").coverage()).isEqualTo(0.03);
        assertThat(presets(result)).containsExactly("AB_HASH");
        assertThat(result.detection().coverage()).isEqualTo(0.6);
        assertThat(result.detection().applied()).isTrue();
        assertThat(result.mode()).isEqualTo(Mode.DETECTED);
    }

    @Test
    void should_discardMainMasterDevelopAndHead() {
        List<String> list = new ArrayList<>(List.of("main", "master", "develop", "HEAD"));
        IntStream.range(0, 20).forEach(i -> list.add("feature/" + (300 + i) + "-x"));
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.detection().branchesScanned()).isEqualTo(20);
    }

    @Test
    void should_notCreateAPrefixCandidate_when_prefixAppearsInFourBranches() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 4).forEach(i -> list.add("wi-" + (700 + i)));
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.detection().candidates()).noneMatch(c -> "PREFIX".equals(c.preset()));
    }

    @Test
    void should_createAPrefixCandidate_when_prefixAppearsInFiveBranches() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 5).forEach(i -> {
            list.add("feature/wi-" + (700 + i) + "-x");
            existing.add(String.valueOf(700 + i));
        });
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        WorkItemPatterns.Candidate prefix = candidate(result, "PREFIX");
        assertThat(prefix.regex()).isEqualTo(BranchPatternMatcher.prefixRegex("wi-"));
        assertThat(prefix.matchedBranches()).isEqualTo(5);
        assertThat(prefix.resolvedBranches()).isEqualTo(5);
    }

    @Test
    void should_keepAtMostFivePrefixCandidates_withTheMostFrequentFirst() {
        List<String> list = new ArrayList<>();
        for (int p = 0; p < 7; p++) {
            int count = 5 + p;
            for (int i = 0; i < count; i++) list.add("pre" + (char) ('a' + p) + "-" + (100 + i));
        }
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        List<String> prefixes = result.detection().candidates().stream().filter(c -> "PREFIX".equals(c.preset()))
                .map(c -> BranchPatternMatcher.literalPrefix(c.regex()).orElseThrow()).toList();
        assertThat(prefixes).hasSize(5).contains("preg-", "pref-").doesNotContain("prea-", "preb-");
        assertThat(result.detection().candidates().size()).isLessThanOrEqualTo(10);
    }

    @Test
    void should_offerJiraKeyOnlyWhenTheOrganizationHasItemsWithThatFormat() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 30).forEach(i -> {
            list.add("feature/PROJ-" + (10 + i) + "-x");
            existing.add("PROJ-" + (10 + i));
        });
        branches(list);

        WorkItemPatterns without = useCase.execute(ORG, Trigger.MANUAL);
        assertThat(without.detection().candidates()).noneMatch(c -> "JIRA_KEY".equals(c.preset()));

        jiraStyle = true;
        organization.setWorkItemPatterns(WorkItemPatterns.defaults());
        WorkItemPatterns with = useCase.execute(ORG, Trigger.MANUAL);
        assertThat(candidate(with, "JIRA_KEY").coverage()).isEqualTo(1.0);
        assertThat(presets(with)).containsExactly("AB_HASH", "JIRA_KEY");
    }

    @Test
    void should_putJiraKeyBeforeNumber_when_bothAreApplied() {
        List<String> list = new ArrayList<>();
        IntStream.range(0, 30).forEach(i -> {
            list.add("feature/login-PROJ-" + (10 + i));
            existing.add("PROJ-" + (10 + i));
        });
        IntStream.range(0, 30).forEach(i -> {
            list.add("feature/" + (5000 + i) + "-x");
            existing.add(String.valueOf(5000 + i));
        });
        jiraStyle = true;
        branches(list);

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(presets(result)).containsExactly("AB_HASH", "JIRA_KEY", "NUMBER");
    }

    @Test
    void should_reapplyDetectedPatterns_whenRunningAgain() {
        branches(numericSample());
        organization.setWorkItemPatterns(new WorkItemPatterns(true, Mode.DETECTED, List.of(
                WorkItemPatterns.Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true),
                WorkItemPatterns.Entry.of(BranchPatternMatcher.prefixRegex("old-"), true)),
                null, null, null, NOW.minusSeconds(86400), null));

        WorkItemPatterns result = useCase.execute(ORG, Trigger.SCHEDULED);

        assertThat(result.mode()).isEqualTo(Mode.DETECTED);
        assertThat(presets(result)).containsExactly("AB_HASH", "NUMBER");
        assertThat(result.detection().applied()).isTrue();
    }

    @Test
    void should_keepEnabledFalse_when_applying() {
        branches(numericSample());
        organization.setWorkItemPatterns(new WorkItemPatterns(false, Mode.DEFAULT, null, null, null, null, null, null));

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.enabled()).isFalse();
        assertThat(result.mode()).isEqualTo(Mode.DETECTED);
    }

    @Test
    void should_retryTheWrite_when_theOrganizationWasChangedMeanwhile() {
        branches(numericSample());
        when(organizations.save(any()))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(inv -> inv.getArgument(0));

        WorkItemPatterns result = useCase.execute(ORG, Trigger.MANUAL);

        assertThat(result.mode()).isEqualTo(Mode.DETECTED);
        verify(organizations, times(2)).save(any());
        verify(sample, times(1)).recentBranches(eq(ORG), any(), eq(5000));
    }

    @Test
    void should_failWithNotFound_when_organizationIsMissing() {
        when(organizations.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("x", Trigger.MANUAL)).isInstanceOf(OrganizationNotFoundException.class);
        verify(organizations, never()).save(any());
    }

    @Test
    void should_lookBackNinetyDays() {
        branches(List.of());

        useCase.execute(ORG, Trigger.MANUAL);

        verify(sample).recentBranches(ORG, NOW.minus(Duration.ofDays(90)), 5000);
    }
}
