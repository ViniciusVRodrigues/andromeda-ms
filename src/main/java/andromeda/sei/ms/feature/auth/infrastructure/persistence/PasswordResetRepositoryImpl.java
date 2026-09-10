package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import andromeda.sei.ms.feature.auth.domain.PasswordReset;
import andromeda.sei.ms.feature.auth.domain.PasswordResetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetRepositoryImpl implements PasswordResetRepository {

    private final PasswordResetMongoRepository mongoRepository;

    @Override
    public PasswordReset save(PasswordReset reset) {
        return toDomain(mongoRepository.save(toDocument(reset)));
    }

    @Override
    public List<PasswordReset> findAllByEmailAndUsedAtIsNull(String email) {
        return mongoRepository.findAllByEmailAndUsedAtIsNull(email).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PasswordReset> findByTokenHash(String tokenHash) {
        return mongoRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    private PasswordReset toDomain(PasswordResetDocument doc) {
        return new PasswordReset(doc.getId(), doc.getEmail(), doc.getTokenHash(),
                doc.getExpiresAt(), doc.getUsedAt(), doc.getCreatedAt());
    }

    private PasswordResetDocument toDocument(PasswordReset reset) {
        return PasswordResetDocument.builder()
                .id(reset.getId())
                .email(reset.getEmail())
                .tokenHash(reset.getTokenHash())
                .expiresAt(reset.getExpiresAt())
                .usedAt(reset.getUsedAt())
                .createdAt(reset.getCreatedAt())
                .build();
    }
}
