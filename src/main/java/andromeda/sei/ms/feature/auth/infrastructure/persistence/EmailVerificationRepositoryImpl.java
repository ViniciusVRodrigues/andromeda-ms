package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import andromeda.sei.ms.feature.auth.domain.EmailVerification;
import andromeda.sei.ms.feature.auth.domain.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryImpl implements EmailVerificationRepository {

    private final EmailVerificationMongoRepository mongoRepository;

    @Override
    public EmailVerification save(EmailVerification verification) {
        EmailVerificationDocument doc = toDocument(verification);
        EmailVerificationDocument saved = mongoRepository.save(doc);
        return toDomain(saved);
    }

    @Override
    public List<EmailVerification> findAllByEmailAndUsedFalse(String email) {
        return mongoRepository.findAllByEmailAndUsedFalse(email)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<EmailVerification> findByEmailAndCodeAndUsedFalse(String email, String code) {
        return mongoRepository.findByEmailAndCodeAndUsedFalse(email, code)
                .map(this::toDomain);
    }

    private EmailVerification toDomain(EmailVerificationDocument doc) {
        return new EmailVerification(
                doc.getId(),
                doc.getEmail(),
                doc.getCode(),
                doc.isUsed(),
                doc.getCreatedAt()
        );
    }

    private EmailVerificationDocument toDocument(EmailVerification verification) {
        return EmailVerificationDocument.builder()
                .id(verification.getId())
                .email(verification.getEmail())
                .code(verification.getCode())
                .used(verification.isUsed())
                .createdAt(verification.getCreatedAt())
                .build();
    }
}