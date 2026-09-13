package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ver a nota em {@code AzureIntegrationDataWiperTest}: estes testes inspecionam o predicado
 * realmente entregue ao Mongo, que e onde mora a classe de bug perigosa (filtro de tenant
 * ausente numa operacao irreversivel).
 */
@ExtendWith(MockitoExtension.class)
class GithubIntegrationDataWiperTest {

    @Mock private MongoTemplate mongoTemplate;

    private GithubIntegrationDataWiper wiper;

    private static final String ORG = "org-1";

    @BeforeEach
    void setUp() {
        wiper = new GithubIntegrationDataWiper(mongoTemplate);
        lenient().when(mongoTemplate.remove(any(Query.class), anyString()))
                .thenReturn(DeleteResult.acknowledged(0));
    }

    @Test
    void toda_remocao_deve_filtrar_por_organizacao() {
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of());

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

    /** Reviews referenciam PRs; PRs e commits referenciam repos. Filhos primeiro. */
    @Test
    void deve_remover_reviews_antes_de_pull_requests_e_repos() {
        GithubPullRequestDocument pr = GithubPullRequestDocument.builder().id("pr-1").build();
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of(pr));

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<String> colecoes = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(any(Query.class), colecoes.capture());

        List<String> ordem = colecoes.getAllValues();
        assertThat(ordem.indexOf("github_reviews")).isLessThan(ordem.indexOf("github_pull_requests"));
        assertThat(ordem.indexOf("github_pull_requests")).isLessThan(ordem.indexOf("github_repos"));
    }

    /**
     * Projeto sem repositorio vinculado nao pode virar "filtro vazio": sem o guard, um escopo
     * por projeto sem bindings cairia para o filtro so de organizacao e apagaria tudo.
     */
    @Test
    void escopo_por_projeto_sem_repositorio_vinculado_nao_deve_remover_nada() {
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of());

        wiper.wipe(ORG, WipeScope.forProject("proj-sem-repo"));

        verify(mongoTemplate, never()).remove(any(Query.class), anyString());
    }
}
