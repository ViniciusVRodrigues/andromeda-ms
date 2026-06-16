package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.EmailVerification;
import andromeda.sei.ms.feature.auth.domain.EmailVerificationRepository;
import andromeda.sei.ms.feature.auth.domain.exception.OtpInvalidOrExpiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfirmEmailVerificationUseCase {

    private final EmailVerificationRepository emailVerificationRepository;

    public void execute(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndCodeAndUsedFalse(email, code)
                .orElseThrow(OtpInvalidOrExpiredException::new);

        verification.markUsed();
        emailVerificationRepository.save(verification);
    }
}