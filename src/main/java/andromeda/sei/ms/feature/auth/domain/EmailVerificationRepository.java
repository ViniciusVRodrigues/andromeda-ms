package andromeda.sei.ms.feature.auth.domain;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository {
    EmailVerification save(EmailVerification verification);
    List<EmailVerification> findAllByEmailAndUsedFalse(String email);
    Optional<EmailVerification> findByEmailAndCodeAndUsedFalse(String email, String code);
}