package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.CreateUserInput;
import andromeda.sei.ms.feature.user.application.dto.CreateUserOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserEmailConflictException;
import andromeda.sei.ms.shared.application.port.EmailService;
import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CreateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateUserUseCase.class);
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INVITE_TOKEN_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FrontendUrlResolver frontendUrlResolver;

    public CreateUserOutput execute(CreateUserInput input, String organizationId) {
        if (userRepository.existsByEmail(input.email())) {
            throw new UserEmailConflictException(input.email());
        }

        String inviteToken = generateInviteToken();

        Instant now = Instant.now();
        User user = new User(
                null,
                organizationId,
                input.email(),
                null,
                null,
                input.roleId(),
                null,
                null,
                null,
                null,
                inviteToken,
                false,
                true,
                null,
                now,
                now
        );

        User saved = userRepository.save(user);

        try {
            emailService.sendInvite(input.email(), null, frontendUrlResolver.resolve() + "/onboard?token=" + inviteToken);
        } catch (Exception e) {
            log.warn("Failed to send invite email to {}: {}", input.email(), e.getMessage());
        }

        return CreateUserOutput.from(saved, inviteToken);
    }

    private String generateInviteToken() {
        StringBuilder sb = new StringBuilder(INVITE_TOKEN_LENGTH);
        for (int i = 0; i < INVITE_TOKEN_LENGTH; i++) {
            sb.append(CHARS.charAt(SECURE_RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
