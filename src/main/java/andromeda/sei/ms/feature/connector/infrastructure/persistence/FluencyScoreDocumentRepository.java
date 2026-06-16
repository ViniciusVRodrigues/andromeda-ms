package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FluencyScoreDocumentRepository extends MongoRepository<FluencyScoreDocument, String> {
}
