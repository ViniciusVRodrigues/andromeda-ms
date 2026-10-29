package andromeda.sei.ms.feature.aiusage.domain;

import java.time.LocalDate;

/**
 * Coexistence of the AIEF with the local agent, per tool (agente-endpoint-v2, design 6): says what to do
 * with one AIEF entry. Without a device of the person the answer is always {@code STORE}.
 */
@FunctionalInterface
public interface AiefCoexistence {

    enum Verdict {
        /** The agent already sent this tool on this agent day: not stored. */
        IGNORE,
        /** Stored and counted. */
        STORE,
        /** Stored and counted, flagged {@code possibleDuplicate} (no tool, but looks like the agent's). */
        STORE_FLAGGED
    }

    Verdict verdict(String organizationId, String userId, LocalDate day, String editor, String model);
}
