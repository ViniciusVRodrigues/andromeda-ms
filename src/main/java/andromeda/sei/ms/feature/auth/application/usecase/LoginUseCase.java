package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.LoginInput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidCredentialsException;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.feature.user.application.port.PasswordVerifier;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordVerifier passwordVerifier;
    private final RecordLoginUseCase recordLoginUseCase;

    @Value("${security.jwt.expiration-ms:86400000}")
    private long expiresInMs;

    public LoginOutput execute(LoginInput input) {
        User user = userRepository.findByEmail(input.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isConfirmed()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordVerifier.verify(input.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        var role = roleRepository.findById(user.getRoleId())
                .orElseThrow(InvalidCredentialsException::new);

        try {
            recordLoginUseCase.execute(user.getId(), user.getOrganizationId(), LoginMethod.PASSWORD);
        } catch (RuntimeException e) {
            log.warn("Login telemetry failed: {}", e.getClass().getSimpleName());
        }

        return new LoginOutput(
                user.getId(),
                user.getOrganizationId(),
                role.getPermissionCodes(),
                expiresInMs
        );
    }
}
