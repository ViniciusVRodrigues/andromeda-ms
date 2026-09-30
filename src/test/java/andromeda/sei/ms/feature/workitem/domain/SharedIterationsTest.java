package andromeda.sei.ms.feature.workitem.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * metrics-correctness 1.3 (shared iterations) — an Azure iteration shared by two teams is stored once
 * per team; every reader must pick the same canonical twin and list the iteration once.
 */
class SharedIterationsTest {

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");
    private static final String PATH = "Proj\\Sprint 25";

    @Test
    void should_pickEarliestCreatedTwin_regardlessOfTeamOrInputOrder() {
        Sprint teamB = sprint("s-b", "team-b", PATH, T0);
        Sprint teamA = sprint("s-a", "team-a", PATH, T0.plusSeconds(60));

        assertThat(SharedIterations.canonical(List.of(teamA, teamB))).containsSame(teamB);
        assertThat(SharedIterations.canonical(List.of(teamB, teamA))).containsSame(teamB);
    }

    @Test
    void should_breakCreatedAtTies_byTeamIdThenId() {
        Sprint teamB = sprint("s-1", "team-b", PATH, T0);
        Sprint teamA = sprint("s-2", "team-a", PATH, T0);
        Sprint teamAOther = sprint("s-0", "team-a", PATH, T0);

        assertThat(SharedIterations.canonical(List.of(teamB, teamA))).containsSame(teamA);
        assertThat(SharedIterations.canonical(List.of(teamB, teamA, teamAOther))).containsSame(teamAOther);
    }

    @Test
    void should_returnEmpty_forNoSprints() {
        assertThat(SharedIterations.canonical(List.of())).isEmpty();
        assertThat(SharedIterations.canonical(null)).isEmpty();
    }

    @Test
    void should_keepOneSprintPerIteration_inInputOrder() {
        Sprint s24 = sprint("s-24", "team-a", "Proj\\Sprint 24", T0);
        Sprint s25b = sprint("s-25b", "team-b", PATH, T0.plusSeconds(5));
        Sprint s25a = sprint("s-25a", "team-a", PATH, T0);
        Sprint s26 = sprint("s-26", "team-b", "Proj\\Sprint 26", T0);

        assertThat(SharedIterations.distinct(List.of(s24, s25b, s25a, s26))).containsExactly(s24, s25a, s26);
    }

    @Test
    void should_notMergeSamePath_acrossProjects() {
        Sprint p1 = sprint("s-1", "team-a", PATH, T0);
        Sprint p2 = sprint("s-2", "team-a", PATH, T0);
        p2.setProjectId("proj-2");

        assertThat(SharedIterations.distinct(List.of(p1, p2))).containsExactly(p1, p2);
    }

    @Test
    void should_resolveNonCanonicalTwin_toTheCanonicalOne() {
        Sprint teamA = sprint("s-a", "team-a", PATH, T0);
        Sprint teamB = sprint("s-b", "team-b", PATH, T0.plusSeconds(60));
        Sprint unrelated = sprint("s-x", "team-a", "Proj\\Sprint 1", T0.minusSeconds(999));

        assertThat(SharedIterations.canonicalTwin(teamB, List.of(teamA, teamB, unrelated))).isSameAs(teamA);
        assertThat(SharedIterations.canonicalTwin(teamB, List.of())).isSameAs(teamB);
    }

    static Sprint sprint(String id, String teamId, String path, Instant createdAt) {
        return new Sprint(id, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS, "iter-" + path, teamId, teamId,
                path, path, T0, T0.plusSeconds(1_209_600), Sprint.TimeFrame.CURRENT, true, 0L, createdAt, createdAt);
    }
}
