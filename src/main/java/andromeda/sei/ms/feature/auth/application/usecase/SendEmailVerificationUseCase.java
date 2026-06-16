package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.EmailVerification;
import andromeda.sei.ms.feature.auth.domain.EmailVerificationRepository;
import andromeda.sei.ms.shared.application.port.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SendEmailVerificationUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;

    public void execute(String email) {
        List<EmailVerification> existing = emailVerificationRepository.findAllByEmailAndUsedFalse(email);
        existing.forEach(v -> {
            v.markUsed();
            emailVerificationRepository.save(v);
        });

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        EmailVerification newVerification = new EmailVerification(null, email, code, false, Instant.now());
        emailVerificationRepository.save(newVerification);

        emailService.sendOtp(email, code);
    }
}