package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.CreateUserInput;
import andromeda.sei.ms.feature.user.application.dto.CreateUserOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserEmailConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CreateUserUseCase {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INVITE_TOKEN_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;

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
                inviteToken,
                false,
                true,
                0L,
                now,
                now
        );

        User saved = userRepository.save(user);
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
