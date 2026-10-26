package andromeda.sei.ms.feature.integration.domain;

import java.util.Map;
import java.util.Set;

/**
 * Porta de remocao permanente dos dados que uma integracao produziu para uma organizacao.
 *
 * <p>Cada integracao implementa a sua na propria fatia vertical (azure/, github/, connector/),
 * de modo que nenhuma feature precise conhecer as colecoes de outra. O caso de uso de wipe
 * conversa so com esta interface.
 *
 * <p><b>Hard delete</b>, por excecao explicita a regra de soft delete do projeto. Duas razoes
 * concretas: o re-sync faz upsert por {@code externalId} e ressuscitaria registros inativos em
 * vez de recria-los, o que espalharia logica de reativacao por todo o pipeline; e soft delete
 * nao libera espaco, justamente nas colecoes maiores. A excecao vale SO para as colecoes de
 * dados de integracao — nenhuma outra entidade muda de semantica.
 *
 * <p>Toda query de delete e obrigatoriamente escopada por {@code organizationId}. A organizacao
 * vem do contexto autenticado, nunca de parametro do cliente.
 */
public interface IntegrationDataWiper {

    /** Qual integracao esta implementacao limpa. */
    Integration integration();

    /**
     * Colecoes que este wiper limpa. Lido pelo teste de arquitetura da exclusao da organizacao,
     * que exige que toda colecao com {@code organizationId} seja coberta por um wiper ou pelo purge.
     */
    Set<String> collections();

    /**
     * Apaga os dados desta integracao na organizacao.
     *
     * @param organizationId tenant; obrigatorio em todo filtro
     * @param scope          o que apagar (binding especifico, integracao inteira, dados de um usuario)
     * @return quantos documentos foram removidos, por colecao, para relatar ao operador
     */
    Map<String, Long> wipe(String organizationId, WipeScope scope);

    /**
     * Recorte do wipe.
     *
     * @param projectId  quando presente, limita a esse projeto/binding; quando null, alcanca a
     *                   integracao inteira na organizacao — unico caso em que caches de nivel de
     *                   organizacao (ex.: azure_org_projects) tambem sao apagados
     * @param userId     quando presente, limita aos dados daquele usuario. Usado pelo conector de
     *                   IA, onde o dado e individual: quem tem apenas CONNECTOR_TOKEN_MANAGE_OWN
     *                   so pode apagar o proprio
     */
    record WipeScope(String projectId, String userId) {

        public static WipeScope wholeIntegration() {
            return new WipeScope(null, null);
        }

        public static WipeScope forProject(String projectId) {
            return new WipeScope(projectId, null);
        }

        public static WipeScope forUser(String userId) {
            return new WipeScope(null, userId);
        }

        public boolean isWholeIntegration() {
            return projectId == null && userId == null;
        }
    }
}
