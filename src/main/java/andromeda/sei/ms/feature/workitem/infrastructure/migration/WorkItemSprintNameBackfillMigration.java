package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One-time migration: popula {@code work_items.sprintName} nos documentos gravados antes de o
 * campo existir. Sem isto, a sprint só apareceria no backlog para itens que o sync incremental
 * voltasse a tocar — e um item antigo e parado pode nunca mais mudar, ficando indefinidamente
 * sem sprint na tela.
 *
 * <p>Diferente de {@link WorkItemParentLinkBackfillMigration}, este backfill <strong>não
 * consulta o Azure</strong>: {@code sprintId} já está no work item e o nome já está na coleção
 * {@code sprints}. É um join local, então não depende de conexão ativa, token válido ou rate
 * limit — roda mesmo para organizações cuja integração esteja expirada.
 *
 * <p>Idempotente: só considera itens com {@code sprintId} presente e {@code sprintName} ausente,
 * e grava apenas quando encontra a sprint correspondente. Rodar de novo após falha parcial
 * encontra menos (ou zero) documentos pendentes.
 *
 * <p>Ordem 22: depois das demais migrations de {@code work_items} (20, 21), apenas para manter o
 * grupo junto nos logs de startup — não há dependência real entre elas.
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class WorkItemSprintNameBackfillMigration implements ApplicationRunner {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;

    /**
     * Todos os status que representam uma integração já estabelecida. Como este backfill não
     * chama o Azure, uma conexão expirada ou em erro tem dados sincronizados tão válidos quanto
     * uma saudável — restringir a {@code CONNECTED} deixaria essas organizações sem sprint na
     * tela sem nenhum ganho.
     */
    private static final List<AzureConnectionStatus> MIGRATABLE_STATUSES = List.of(
            AzureConnectionStatus.CONNECTED,
            AzureConnectionStatus.EXPIRED,
            AzureConnectionStatus.ERROR);

    @Override
    public void run(ApplicationArguments args) {
        List<AzureConnection> connections = MIGRATABLE_STATUSES.stream()
                .flatMap(status -> azureConnectionRepository.findAllByStatusAndActiveTrue(status).stream())
                .toList();

        if (connections.isEmpty()) {
            log.info("WorkItemSprintNameBackfillMigration: no Azure connections — nothing to migrate");
            return;
        }

        int totalUpdated = 0;
        for (AzureConnection connection : connections) {
            String orgId = connection.getOrganizationId();
            List<AzureProjectBinding> bindings =
                    azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue(orgId);

            for (AzureProjectBinding binding : bindings) {
                try {
                    totalUpdated += migrateProject(orgId, binding.getProjectId());
                } catch (Exception e) {
                    log.error("WorkItemSprintNameBackfillMigration: failed for org={} project={}",
                            orgId, binding.getProjectId(), e);
                }
            }
        }

        log.info("WorkItemSprintNameBackfillMigration: migration complete, {} work_items updated", totalUpdated);
    }

    private int migrateProject(String orgId, String projectId) {
        List<WorkItem> pending = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId)
                .stream()
                .filter(WorkItem::isActive)
                .filter(item -> item.getSprintId() != null && item.getSprintName() == null)
                .toList();

        if (pending.isEmpty()) return 0;

        Map<String, Sprint> sprintsById = sprintRepository
                .findAllByOrganizationIdAndProjectId(orgId, projectId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Sprint::getId, Function.identity(), (a, b) -> a));

        int updated = 0;
        for (WorkItem item : pending) {
            Sprint sprint = sprintsById.get(item.getSprintId());
            /* sprintId órfão (sprint removida do Azure, ou ainda não sincronizada): deixa como
             * está para a próxima execução, em vez de apagar o vínculo. */
            if (sprint == null) continue;
            item.setSprintName(sprint.getName());
            workItemRepository.save(item);
            updated++;
        }

        log.info("WorkItemSprintNameBackfillMigration: org={} project={} candidates={} updated={}",
                orgId, projectId, pending.size(), updated);
        return updated;
    }
}
