package andromeda.sei.ms.feature.agent.application.usecase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The AIEF side of approving a pairing (agente-endpoint-v2, design 6): the cutover day and the supersession. */
public interface AiefCutover {

    /** Local day of the approval, or the next one when the person already has a validated AIEF day today. */
    LocalDate cutoverDay(String organizationId, String userId, String timezone, Instant approvedAt);

    /** Without AIEF knowledge: the local day of the approval, nothing superseded. */
    AiefCutover NONE = new AiefCutover() {
        @Override
        public LocalDate cutoverDay(String organizationId, String userId, String timezone, Instant approvedAt) {
            return approvedAt.atZone(ZoneId.of(timezone)).toLocalDate();
        }

    };
}
