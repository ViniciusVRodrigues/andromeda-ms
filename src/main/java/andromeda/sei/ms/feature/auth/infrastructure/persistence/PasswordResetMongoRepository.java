package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetMongoRepository extends MongoRepository<PasswordResetDocument, String> {

    List<PasswordResetDocument> findAllByEmailAndUsedAtIsNull(String email);

    Optional<PasswordResetDocument> findByTokenHash(String tokenHash);
}
