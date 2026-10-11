package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.IntegrationsDashboardV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.OrganizationDashboardV2Response;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Organization and integrations outputs → v2 responses, adding {@code asOf} (ISO-8601) and {@code dataFreshness}. */
@Component
public class OrganizationDashboardsV2Mapper {

    public OrganizationDashboardV2Response toResponse(OrganizationDashboardV2Output out, DataFreshness freshness) {
        return new OrganizationDashboardV2Response(out.period(), out.kpis(), out.previous(), out.portfolio(),
                out.delivery(), out.engineering(), out.ai(), out.integrations(), iso(out.asOf()), freshness);
    }

    public IntegrationsDashboardV2Response toResponse(IntegrationsDashboardV2Output out, DataFreshness freshness) {
        return new IntegrationsDashboardV2Response(out.connections(), out.people(), out.alertCount(), out.repos(),
                out.sync(), out.unlinkedCommits(), iso(out.asOf()), freshness);
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
