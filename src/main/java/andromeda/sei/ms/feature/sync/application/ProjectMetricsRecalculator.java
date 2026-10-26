package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.usecase.CalculatePullRequestMetricsUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recalcula todas as metricas derivadas de um projeto ao fim de um sync (design D4): metricas
 * de work item (cycle/lead time), de sprint, tokens por story point e de pull request.
 *
 * <p>E chamado pelo orquestrador — e nao por cada trigger — para que todo caminho de sync
 * (refresh, manual, por binding, agendado) termine no mesmo recalculo. Cada calculo e isolado:
 * a falha de um nao impede os outros nem altera o status do sync.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectMetricsRecalculator {

    private final CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    private final CalculateSprintMetricsUseCase calculateSprintMetricsUseCase;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;
    private final CalculatePullRequestMetricsUseCase calculatePullRequestMetricsUseCase;
    private final VcsReadPort vcsReadPort;

    public void recalculate(String organizationId, String projectId) {
        isolated("work item metrics", organizationId, projectId,
                () -> calculateWorkItemMetricsUseCase.execute(organizationId, projectId));
        isolated("sprint metrics", organizationId, projectId,
                () -> calculateSprintMetricsUseCase.execute(organizationId, projectId));
        isolated("AI ratios", organizationId, projectId,
                () -> calculateAiRatiosUseCase.execute(organizationId, projectId));
        isolated("pull request metrics", organizationId, projectId,
                () -> recalculatePullRequestMetrics(organizationId, projectId));
    }

    private void recalculatePullRequestMetrics(String organizationId, String projectId) {
        List<VcsRepository> repos = vcsReadPort.repositoriesOfProject(organizationId, projectId);
        for (VcsRepository repo : repos) {
            isolated("pull request metrics of repo " + repo.getDisplayName(), organizationId, projectId,
                    () -> calculatePullRequestMetricsUseCase.execute(organizationId, repo.getId()));
        }
    }

    private void isolated(String calculation, String organizationId, String projectId, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("{} calculation failed for org={} project={}", calculation, organizationId, projectId, e);
        }
    }
}
