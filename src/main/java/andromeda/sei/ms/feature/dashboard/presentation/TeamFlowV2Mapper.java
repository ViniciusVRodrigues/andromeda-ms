package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.TeamFlowV2Response;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import org.springframework.stereotype.Component;

/** Team flow output → response, adding {@code asOf} (ISO-8601) and {@code dataFreshness}. */
@Component
public class TeamFlowV2Mapper {

    public TeamFlowV2Response toResponse(TeamFlowV2Output out, DataFreshness freshness) {
        return new TeamFlowV2Response(out.sprint(), out.processTemplate(), out.states(), out.cfd(), out.stateTimes(),
                out.rework(), out.scope(), out.reviewQueue(), out.asOf() == null ? null : out.asOf().toString(),
                freshness);
    }
}
