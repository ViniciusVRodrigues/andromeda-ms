package andromeda.sei.ms.feature.aiusage.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Queries of the agent and AIEF coexistence, the active task and the manual correction
 * (agente-endpoint-v2). Separate from {@link AgentAIUsageRepository} on purpose: it does not touch the ingest port.
 * The AIEF side is always asked by tool, through the AIEF {@code editors} that stand for it.
 */
public interface AgentUsageQueries {

    /** Whether the user has an active AIEF row on the day whose editor is one of these. */
    boolean existsActiveAief(String organizationId, String userId, LocalDate day, Collection<String> editors);

    /** Whether one of those AIEF rows is in an allocation the person validated (it then wins over the agent). */
    boolean existsAiefInValidatedAllocation(String organizationId, String userId, LocalDate day,
                                            Collection<String> editors);

    /**
     * Active AIEF rows of the day with one of these editors and no allocation become
     * {@code active=false, supersededBy=AGENT}; returns how many.
     */
    long supersedeAief(String organizationId, String userId, LocalDate day, Collection<String> editors);

    /** Whether the user has an active agent row of this tool on the day. */
    boolean existsActiveAgentRow(String organizationId, String userId, LocalDate day, String tool);

    /** Models the agent sent for the user on the day (active rows). */
    Set<String> agentModelsOfDay(String organizationId, String userId, LocalDate day);

    /** Active agent rows of the user on the day with no allocation. */
    List<AIUsage> findUnallocatedAgentRows(String organizationId, String userId, LocalDate day);

    /** Active agent rows of the user whose hour is at or after {@code since}. */
    List<AIUsage> findActiveAgentRowsSince(String organizationId, String userId, Instant since);

    /** Stamps the resolved task on these rows (only those of the organization). */
    void stampTask(String organizationId, Collection<String> ids, String workItemId, String projectId,
                   String taskRef, String attributionSource);
}
