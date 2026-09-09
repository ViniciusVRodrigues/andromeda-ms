package andromeda.sei.ms.feature.auth.domain;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRepository {
    PasswordReset save(PasswordReset reset);
    List<PasswordReset> findAllByEmailAndUsedAtIsNull(String email);
    Optional<PasswordReset> findByTokenHash(String tokenHash);
}
