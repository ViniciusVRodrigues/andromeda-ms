package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O wipe e a unica operacao irreversivel da plataforma: um {@code remove()} com filtro errado
 * apaga dado de outra organizacao sem volta. Estes testes inspecionam o {@link Query} realmente
 * entregue ao {@link MongoTemplate} e exigem que TODA remocao carregue {@code organizationId}.
 *
 * <p>Sem banco de proposito. O projeto esta sem cobertura de persistencia desde o Spring Boot 4
 * (que removeu {@code @DataMongoTest}; os 15 testes de repositorio seguem {@code @Disabled}
 * aguardando reescrita). Escrever mais um teste desabilitado aqui seria aparencia de cobertura
 * justamente no ponto mais perigoso — entao verificamos o predicado montado, que e onde mora a
 * classe de bug que importa: filtro de tenant ausente ou frouxo.
 */
@ExtendWith(MockitoExtension.class)
class AzureIntegrationDataWiperTest {

    @Mock private MongoTemplate mongoTemplate;

    private AzureIntegrationDataWiper wiper;

    private static final String ORG = "org-1";

    @BeforeEach
    void setUp() {
        wiper = new AzureIntegrationDataWiper(mongoTemplate);
        when(mongoTemplate.remove(any(Query.class), anyString()))
                .thenReturn(DeleteResult.acknowledged(0));
    }

    /** Sem work items, as consultas de id devolvem vazio e o fluxo segue. */
    private void semWorkItems() {
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of());
    }

    @Test
    void toda_remocao_deve_filtrar_por_organizacao() {
        semWorkItems();

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), anyString());

        assertThat(captor.getAllValues()).isNotEmpty();
        for (Query q : captor.getAllValues()) {
            assertThat(q.getQueryObject().get("organizationId"))
                    .as("remocao sem filtro de tenant apagaria dado de outra organizacao: %s", q)
                    .isEqualTo(ORG);
        }
    }

    @Test
    void deve_restringir_work_items_a_fonte_azure_para_nao_atingir_jira() {
        semWorkItems();

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<String> colecoes = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), colecoes.capture());

        int idx = colecoes.getAllValues().indexOf("work_items");
        assertThat(idx).as("o wipe precisa remover de work_items").isGreaterThanOrEqualTo(0);

        Query workItems = captor.getAllValues().get(idx);
        assertThat(workItems.getQueryObject().get("source"))
                .as("sem o discriminador de fonte, o wipe de Azure apagaria work items do Jira")
                .isEqualTo(WorkItem.Source.AZURE_DEVOPS);
    }

    @Test
    void escopo_por_projeto_deve_filtrar_por_projeto() {
        semWorkItems();

        wiper.wipe(ORG, WipeScope.forProject("proj-1"));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<String> colecoes = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), colecoes.capture());

        for (int i = 0; i < colecoes.getAllValues().size(); i++) {
            String colecao = colecoes.getAllValues().get(i);
            Query q = captor.getAllValues().get(i);
            if (colecao.equals("work_items") || colecao.equals("sprints") || colecao.equals("sprint_metrics")) {
                assertThat(q.getQueryObject().get("projectId"))
                        .as("%s deveria estar restrita ao projeto do escopo", colecao)
                        .isEqualTo("proj-1");
            }
        }
    }

    /**
     * Caches de nivel de organizacao nao pertencem a binding nenhum: limpar um projeto nao pode
     * derrubar a lista de projetos/usuarios Azure que os outros bindings da org tambem usam.
     */
    @Test
    void escopo_por_projeto_nao_deve_apagar_caches_da_organizacao() {
        semWorkItems();

        wiper.wipe(ORG, WipeScope.forProject("proj-1"));

        verify(mongoTemplate, never()).remove(any(Query.class), org.mockito.ArgumentMatchers.eq("azure_org_projects"));
        verify(mongoTemplate, never()).remove(any(Query.class), org.mockito.ArgumentMatchers.eq("azure_org_users"));
    }

    @Test
    void escopo_de_integracao_inteira_deve_apagar_caches_da_organizacao() {
        semWorkItems();

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        verify(mongoTemplate).remove(any(Query.class), org.mockito.ArgumentMatchers.eq("azure_org_projects"));
        verify(mongoTemplate).remove(any(Query.class), org.mockito.ArgumentMatchers.eq("azure_org_users"));
    }

    /** Transitions saem antes dos work items: senao uma falha no meio deixa transition orfa. */
    @Test
    void deve_remover_transitions_antes_dos_work_items() {
        WorkItemDocument doc = WorkItemDocument.builder().id("wi-1").build();
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of(doc));

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<String> colecoes = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(any(Query.class), colecoes.capture());

        List<String> ordem = colecoes.getAllValues();
        assertThat(ordem.indexOf("work_item_transitions"))
                .isLessThan(ordem.indexOf("work_items"));
    }
}
