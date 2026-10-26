package andromeda.sei.ms.architecture;

import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureIntegrationDataWiper;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.AiConnectorIntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.organization.infrastructure.persistence.MongoOrganizationDataPurger;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsIntegrationDataWiper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * excluir-organizacao 4.2 - toda classe {@code @Document} com campo {@code organizationId} tem de
 * ser apagada quando a organizacao e excluida: pelo purge ({@link MongoOrganizationDataPurger}), por
 * um {@link IntegrationDataWiper} ou estar na {@link #ALLOWLIST} com o motivo. Uma colecao nova com
 * {@code organizationId} que ninguem lembre de incluir faz este teste falhar.
 */
class OrganizationPurgeCoverageArchitectureTest {

    private static final String BASE_PACKAGE = "andromeda.sei.ms";

    /**
     * Colecoes com {@code organizationId} que o purge NAO apaga, com o motivo. Vazia hoje: toda
     * colecao da organizacao e apagada. {@code permissions} e global e nao tem o campo;
     * {@code roles} globais tem {@code organizationId} nulo e nao casam com o filtro, e os da
     * organizacao sao apagados normalmente; {@code email_verifications} e {@code password_resets}
     * nao tem {@code organizationId} (chaveados por e-mail, expiram por TTL).
     */
    private static final Map<String, String> ALLOWLIST = Map.of();

    @Test
    void every_document_with_organizationId_is_covered_by_the_purge() {
        List<String> missing = uncovered(scanDocuments(), coveredCollections(), ALLOWLIST.keySet());

        assertThat(missing)
                .as("@Document com organizationId fora do purge: inclua-a em MongoOrganizationDataPurger "
                        + "(ou num IntegrationDataWiper) ou na allowlist deste teste, com o motivo")
                .isEmpty();
    }

    @Test
    void the_scan_finds_the_organization_owned_documents() {
        assertThat(scanDocuments().stream().map(OrganizationPurgeCoverageArchitectureTest::collectionOf))
                .contains("users", "projects", "work_items", "ai_usages", "integration_sync_logs");
    }

    @Test
    void a_new_document_with_organizationId_left_out_of_the_purge_is_reported() {
        List<Class<?>> documents = new ArrayList<>(scanDocuments());
        documents.add(NewOrgOwnedDocument.class);
        documents.add(GlobalDocument.class);

        List<String> missing = uncovered(documents, coveredCollections(), Set.of());

        assertThat(missing).containsExactly("brand_new_collection");
    }

    @Test
    void an_allowlisted_document_is_not_reported() {
        List<String> missing = uncovered(List.of(NewOrgOwnedDocument.class), Set.of(), Set.of("brand_new_collection"));

        assertThat(missing).isEmpty();
    }

    private static List<String> uncovered(List<Class<?>> documents, Set<String> covered, Set<String> allowlist) {
        return documents.stream()
                .filter(OrganizationPurgeCoverageArchitectureTest::hasOrganizationId)
                .map(OrganizationPurgeCoverageArchitectureTest::collectionOf)
                .filter(c -> !covered.contains(c) && !allowlist.contains(c))
                .sorted()
                .toList();
    }

    private static Set<String> coveredCollections() {
        Set<String> covered = new TreeSet<>(new MongoOrganizationDataPurger(null).collections());
        List<IntegrationDataWiper> wipers = List.of(
                new AzureIntegrationDataWiper(null),
                new AiConnectorIntegrationDataWiper(null),
                new VcsIntegrationDataWiper(null, VcsProvider.GITHUB),
                new VcsIntegrationDataWiper(null, VcsProvider.AZURE_REPOS));
        wipers.forEach(w -> covered.addAll(w.collections()));
        return covered;
    }

    private static List<Class<?>> scanDocuments() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            // classes de teste (como as de prova deste arquivo) nao sao colecoes reais
            if (bd.getBeanClassName().contains("Test")) continue;
            try {
                classes.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return classes;
    }

    private static boolean hasOrganizationId(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                if (field.getName().equals("organizationId")) return true;
            }
        }
        return false;
    }

    private static String collectionOf(Class<?> type) {
        return AnnotatedElementUtils.findMergedAnnotation(type, Document.class).collection();
    }

    @Document(collection = "brand_new_collection")
    static class NewOrgOwnedDocument {
        String organizationId;
    }

    @Document(collection = "some_global_collection")
    static class GlobalDocument {
        String name;
    }
}
