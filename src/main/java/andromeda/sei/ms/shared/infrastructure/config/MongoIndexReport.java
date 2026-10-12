package andromeda.sei.ms.shared.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registra no startup quantos índices cada coleção tem, logo depois de o
 * {@link DeclaredIndexInitializer} (ordem 9) ter criado os {@code @CompoundIndex}/{@code @Indexed}
 * declarados nos documentos.
 *
 * <p>Existe porque a ausência de índices é silenciosa: por muito tempo nenhuma declaração do
 * projeto virou índice de verdade ({@code MongoConfig} estende
 * {@code AbstractMongoClientConfiguration}, cujo mapping context não cria índices) e nada no log
 * indicava isso. Uma coleção com apenas {@code _id_} depois deste relatório é sinal de que suas
 * declarações não foram aplicadas.
 *
 * <p>Não cria nem altera nada: só observa. Uma falha de criação (tipicamente um índice
 * {@code unique} sobre dados que já têm duplicatas) aparece como WARN do
 * {@code DeclaredIndexInitializer}, não aqui, e não interrompe o startup. Os índices dos
 * initializers explícitos (ordem 11/12) ainda não entram nesta contagem.
 *
 * <p>Ordem 10: antes das migrations de dados (20+).
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class MongoIndexReport implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<String> collections = mongoTemplate.getCollectionNames().stream().sorted().toList();

        if (collections.isEmpty()) {
            log.info("MongoIndexReport: base ainda sem coleções — índices serão criados no primeiro uso");
            return;
        }

        int totalIndexes = 0;
        List<String> onlyDefaultIndex = new java.util.ArrayList<>();

        for (String collection : collections) {
            try {
                long count = mongoTemplate.getCollection(collection).listIndexes().into(new java.util.ArrayList<>()).size();
                totalIndexes += count;
                /* 1 = só o _id_ automático do Mongo. Nem toda coleção declara índices, então
                 * isto é um aviso a investigar, não necessariamente um defeito. */
                if (count <= 1) onlyDefaultIndex.add(collection);
            } catch (Exception e) {
                log.warn("MongoIndexReport: não foi possível listar índices de {}: {}", collection, e.getMessage());
            }
        }

        log.info("MongoIndexReport: {} coleções, {} índices no total", collections.size(), totalIndexes);
        if (!onlyDefaultIndex.isEmpty()) {
            log.info("MongoIndexReport: apenas o índice _id_ em {} — confira se essas coleções "
                    + "deveriam declarar índices", onlyDefaultIndex);
        }
    }
}
