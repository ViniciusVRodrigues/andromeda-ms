package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountInput;
import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidInviteTokenException;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompleteAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteAccountUseCase.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final RecordLoginUseCase recordLoginUseCase;

    @Value("${security.jwt.expiration-ms:86400000}")
    private long expiresInMs;

    public CompleteAccountOutput execute(CompleteAccountInput input) {
        User user = userRepository.findByInviteToken(input.token())
                .orElseThrow(InvalidInviteTokenException::new);

        user.setName(input.name());
        user.setPasswordHash(passwordHasher.hash(input.password()));
        user.setInviteToken(null);
        user.setConfirmed(true);
        userRepository.save(user);

        var role = roleRepository.findById(user.getRoleId())
                .orElseThrow(InvalidInviteTokenException::new);

        try {
            recordLoginUseCase.execute(user.getId(), user.getOrganizationId(), LoginMethod.INVITE);
        } catch (RuntimeException e) {
            log.warn("Invite-login telemetry failed: {}", e.getClass().getSimpleName());
        }

        return new CompleteAccountOutput(
                user.getId(),
                user.getOrganizationId(),
                role.getPermissionCodes(),
                expiresInMs
        );
    }
}
