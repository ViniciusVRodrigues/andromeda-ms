package andromeda.sei.ms.feature.connector.infrastructure.persistence;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uso de IA e dado individual e, ao contrario de Azure e GitHub, <b>nao volta com re-sync</b>:
 * chega por upload do connector. Um filtro de usuario ausente aqui apaga o historico de IA de
 * toda a organizacao quando o pedido era "apague o meu" — perda definitiva.
 *
 * <p>Ver a nota em {@code AzureIntegrationDataWiperTest} sobre a ausencia de banco nestes testes.
 */
@ExtendWith(MockitoExtension.class)
class AiConnectorIntegrationDataWiperTest {

    @Mock private MongoTemplate mongoTemplate;

    private AiConnectorIntegrationDataWiper wiper;

    private static final String ORG = "org-1";

    @BeforeEach
    void setUp() {
        wiper = new AiConnectorIntegrationDataWiper(mongoTemplate);
        when(mongoTemplate.remove(any(Query.class), anyString()))
                .thenReturn(DeleteResult.acknowledged(0));
    }

    private List<Query> remocoes() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), anyString());
        return captor.getAllValues();
    }

    @Test
    void toda_remocao_deve_filtrar_por_organizacao() {
        wiper.wipe(ORG, WipeScope.wholeIntegration());

        for (Query q : remocoes()) {
            assertThat(q.getQueryObject().get("organizationId"))
                    .as("remocao sem filtro de tenant apagaria dado de outra organizacao: %s", q)
                    .isEqualTo(ORG);
        }
    }

    /** Escopo OWN: quem so tem CONNECTOR_TOKEN_MANAGE_OWN nao pode tocar no dado de ninguem mais. */
    @Test
    void escopo_por_usuario_deve_filtrar_todas_as_colecoes_por_usuario() {
        wiper.wipe(ORG, WipeScope.forUser("user-1"));

        List<Query> queries = remocoes();
        assertThat(queries).hasSize(3);
        for (Query q : queries) {
            assertThat(q.getQueryObject().get("userId"))
                    .as("sem filtro de usuario o wipe individual apagaria o uso de IA da org inteira: %s", q)
                    .isEqualTo("user-1");
        }
    }

    /** Escopo ALL: sem userId no filtro, para alcancar a organizacao inteira. */
    @Test
    void escopo_de_organizacao_nao_deve_filtrar_por_usuario() {
        wiper.wipe(ORG, WipeScope.wholeIntegration());

        for (Query q : remocoes()) {
            assertThat(q.getQueryObject().get("userId")).isNull();
        }
    }

    /** fluency_scores e derivado de ai_usages: manter o score orfao seria numero inexplicavel. */
    @Test
    void deve_apagar_as_tres_colecoes_incluindo_fluency() {
        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<String> colecoes = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(any(Query.class), colecoes.capture());

        assertThat(colecoes.getAllValues())
                .containsExactlyInAnyOrder("ai_usages", "ai_usage_allocations", "fluency_scores");
    }
}
