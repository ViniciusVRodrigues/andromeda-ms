package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.infrastructure.persistence.AgentDeviceAuthorizationDocument;
import andromeda.sei.ms.feature.agent.infrastructure.persistence.AgentDeviceDocument;
import andromeda.sei.ms.feature.agent.infrastructure.persistence.AgentUploadBatchDocument;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import andromeda.sei.ms.feature.auth.infrastructure.persistence.OAuthLinkTicketDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureConnectionDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureOrgProjectDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureOrgUserDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.ConnectorTokenDocument;
import andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence.DashboardLayoutDocument;
import andromeda.sei.ms.feature.github.infrastructure.persistence.GithubConfigDocument;
import andromeda.sei.ms.feature.integration.infrastructure.persistence.IntegrationSyncLogDocument;
import andromeda.sei.ms.feature.project.infrastructure.persistence.ProjectDocument;
import andromeda.sei.ms.feature.provideraccount.infrastructure.persistence.UserProviderAccountDocument;
import andromeda.sei.ms.feature.role.infrastructure.persistence.RoleDocument;
import andromeda.sei.ms.feature.usage.infrastructure.persistence.FunnelEventDocument;
import andromeda.sei.ms.feature.usage.infrastructure.persistence.UsageEventDocument;
import andromeda.sei.ms.feature.user.infrastructure.persistence.UserDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsCommitDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepoBindingDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepositoryDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsReviewEventDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.SprintDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.SprintMetricsDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemActivityDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemTransitionDocument;
import andromeda.sei.ms.feature.organization.application.port.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hard delete de tudo o que pertence a uma organizacao excluida (excluir-organizacao, fase 2).
 *
 * <p><b>Hard delete</b>, por excecao explicita a regra de soft delete do projeto, decidida pela
 * pessoa: "excluir a organizacao" tem de apagar de verdade os dados dela. E a segunda excecao,
 * depois do "Limpar dados" das integracoes ({@code IntegrationDataWiper}), e vale so para a
 * exclusao da organizacao. O documento em {@code organizations} nao e tocado aqui: vira lapide.
 *
 * <p>Toda query de delete e escopada por {@code organizationId} exato e o valor nunca vem do
 * cliente. {@code roles} globais ({@code organizationId} nulo) e {@code permissions} ficam, pois
 * nenhum filtro por id de organizacao os alcanca. Filhos antes dos pais, para que uma falha no
 * meio nao deixe referencia pendurada; a operacao e idempotente e pode ser repetida.
 *
 * <p>As colecoes vem dos {@code @Document} das proprias classes: nao ha nome de colecao digitado
 * aqui. O teste de arquitetura exige que toda {@code @Document} com {@code organizationId} esteja
 * aqui, num wiper ou na allowlist comentada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoOrganizationDataPurger implements OrganizationDataPurger {

    private static final Class<?>[] DOCUMENTS = {
            AgentDeviceAuthorizationDocument.class,
            AgentDeviceDocument.class,
            AgentUploadBatchDocument.class,
            VcsReviewEventDocument.class,
            VcsPullRequestDocument.class,
            VcsCommitDocument.class,
            VcsRepoBindingDocument.class,
            VcsRepositoryDocument.class,
            WorkItemTransitionDocument.class,
            WorkItemActivityDocument.class,
            WorkItemDocument.class,
            SprintMetricsDocument.class,
            SprintDocument.class,
            AzureProjectBindingDocument.class,
            AzureOrgProjectDocument.class,
            AzureOrgUserDocument.class,
            AzureConnectionDocument.class,
            GithubConfigDocument.class,
            AiUsageAllocationDocument.class,
            AiUsageDocument.class,
            ConnectorTokenDocument.class,
            UsageEventDocument.class,
            FunnelEventDocument.class,
            IntegrationSyncLogDocument.class,
            DashboardLayoutDocument.class,
            ProjectDocument.class,
            OAuthLinkTicketDocument.class,
            UserProviderAccountDocument.class,
            RoleDocument.class,
            UserDocument.class
    };

    private final MongoTemplate mongoTemplate;

    @Override
    public Map<String, Long> purge(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("organizationId obrigatorio");
        }
        Map<String, Long> removed = new LinkedHashMap<>();
        for (Class<?> document : DOCUMENTS) {
            String collection = collectionOf(document);
            long count = mongoTemplate.remove(
                    Query.query(Criteria.where("organizationId").is(organizationId)), collection).getDeletedCount();
            removed.put(collection, count);
        }
        log.info("Purge da organizacao: org={} removidos={}", organizationId, removed);
        return removed;
    }

    @Override
    public Set<String> collections() {
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(DOCUMENTS).forEach(d -> names.add(collectionOf(d)));
        return names;
    }

    private static String collectionOf(Class<?> document) {
        Document annotation = AnnotatedElementUtils.findMergedAnnotation(document, Document.class);
        if (annotation == null || annotation.collection().isBlank()) {
            throw new IllegalStateException("@Document sem collection: " + document.getName());
        }
        return annotation.collection();
    }
}
