package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hard delete dos dados que o Azure DevOps produziu numa organizacao.
 *
 * <p>A ordem e filhos antes dos pais (transitions -> work items): uma falha no meio nao pode
 * deixar transition apontando para work item que ja nao existe.
 *
 * <p>{@code work_items} e compartilhada com outras fontes ({@link WorkItem.Source}), entao todo
 * filtro carrega {@code source = AZURE_DEVOPS} — dado de Jira nunca e atingido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureIntegrationDataWiper implements IntegrationDataWiper {

    private final MongoTemplate mongoTemplate;

    @Override
    public Integration integration() {
        return Integration.AZURE_DEVOPS;
    }

    @Override
    public Map<String, Long> wipe(String organizationId, WipeScope scope) {
        Map<String, Long> removidos = new LinkedHashMap<>();

        /* Transitions referenciam work items por id, e nao carregam projectId. No escopo por
         * projeto e preciso resolver os ids primeiro — por isso esta consulta antecede o delete. */
        List<String> workItemIds = workItemIds(organizationId, scope);

        if (!workItemIds.isEmpty()) {
            Query transitions = Query.query(Criteria.where("organizationId").is(organizationId)
                    .and("workItemId").in(workItemIds));
            removidos.put("work_item_transitions",
                    mongoTemplate.remove(transitions, "work_item_transitions").getDeletedCount());
        } else {
            removidos.put("work_item_transitions", 0L);
        }

        removidos.put("work_items",
                mongoTemplate.remove(workItemQuery(organizationId, scope), "work_items").getDeletedCount());

        removidos.put("sprint_metrics",
                mongoTemplate.remove(porProjeto(organizationId, scope), "sprint_metrics").getDeletedCount());
        removidos.put("sprints",
                mongoTemplate.remove(porProjeto(organizationId, scope), "sprints").getDeletedCount());

        /* Caches de nivel de organizacao: nao pertencem a binding nenhum, entao so somem quando
         * a integracao inteira e limpa. */
        if (scope.isWholeIntegration()) {
            Query daOrg = Query.query(Criteria.where("organizationId").is(organizationId));
            removidos.put("azure_org_projects",
                    mongoTemplate.remove(daOrg, "azure_org_projects").getDeletedCount());
            removidos.put("azure_org_users",
                    mongoTemplate.remove(daOrg, "azure_org_users").getDeletedCount());
        }

        log.info("Wipe Azure concluido: org={} scope={} removidos={}", organizationId, scope, removidos);
        return removidos;
    }

    private List<String> workItemIds(String organizationId, WipeScope scope) {
        Query q = workItemQuery(organizationId, scope);
        q.fields().include("_id");
        List<WorkItemDocument> docs = mongoTemplate.find(q, WorkItemDocument.class);
        List<String> ids = new ArrayList<>(docs.size());
        for (WorkItemDocument d : docs) ids.add(d.getId());
        return ids;
    }

    private Query workItemQuery(String organizationId, WipeScope scope) {
        Criteria c = Criteria.where("organizationId").is(organizationId)
                .and("source").is(WorkItem.Source.AZURE_DEVOPS);
        if (scope.projectId() != null) c = c.and("projectId").is(scope.projectId());
        return Query.query(c);
    }

    private Query porProjeto(String organizationId, WipeScope scope) {
        Criteria c = Criteria.where("organizationId").is(organizationId);
        if (scope.projectId() != null) c = c.and("projectId").is(scope.projectId());
        return Query.query(c);
    }
}
