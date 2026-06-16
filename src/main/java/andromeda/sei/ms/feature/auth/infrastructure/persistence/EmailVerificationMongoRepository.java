package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationMongoRepository extends MongoRepository<EmailVerificationDocument, String> {
    List<EmailVerificationDocument> findAllByEmailAndUsedFalse(String email);
    Optional<EmailVerificationDocument> findByEmailAndCodeAndUsedFalse(String email, String code);
}
