package andromeda.sei.ms.feature.aiusage.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Port of the extension x agent overlap reconciliation (extensao-fora-dos-totais). Every method is
 * scoped by {@code organizationId}.
 */
public interface AiefOverlapRepository {

    /** Minimal projection of an AIEF (extension) row that may overlap an agent row. */
    record Candidate(String id, String userId, LocalDate day, String editor, boolean active, String supersededBy) {}

    /** An active agent row key: who, which day, which tool. */
    record AgentCoverage(String userId, LocalDate day, String tool) {}

    /**
     * AIEF rows ({@code source} null) of the given editors that are active or were turned off by the
     * agent, optionally only from {@code fromDay} on (null = whole history).
     */
    List<Candidate> findCandidates(String organizationId, Collection<String> editors, LocalDate fromDay);

    /** Distinct (user, day, tool) with an active agent row, for the users and days of the candidates. */
    Set<AgentCoverage> findAgentCoverage(String organizationId, Collection<Candidate> candidates,
                                         Collection<String> tools);

    /** Turns off (active=false, supersededBy=AGENT) the ids still active. Returns how many changed. */
    long deactivate(String organizationId, Collection<String> ids);

    /** Turns on (active=true, supersededBy cleared) the ids still off by the agent. Returns how many changed. */
    long reactivate(String organizationId, Collection<String> ids);
}
