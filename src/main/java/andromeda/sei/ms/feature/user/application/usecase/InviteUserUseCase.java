package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.feature.user.application.dto.InviteUserInput;
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
public class InviteUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(InviteUserUseCase.class);
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final FrontendUrlResolver frontendUrlResolver;

    public CreateUserOutput execute(InviteUserInput input, String organizationId) {
        if (userRepository.existsByEmail(input.email())) {
            throw new UserEmailConflictException(input.email());
        }

        Role role = roleRepository.findByNameAndOrganizationId(input.roleName(), organizationId)
                .orElseThrow(() -> new RoleNotFoundException(input.roleName()));

        String inviteToken = generateToken();
        Instant now = Instant.now();
        User user = new User(
                null,
                organizationId,
                input.email(),
                null,
                null,
                role.getId(),
                input.seniority(),
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
            emailService.sendInvite(input.email(), null, frontendUrlResolver.resolve() + "/invite/complete?token=" + inviteToken);
        } catch (Exception e) {
            log.warn("Failed to send invite email to {}: {}", input.email(), e.getMessage());
        }

        return CreateUserOutput.from(saved, inviteToken);
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
