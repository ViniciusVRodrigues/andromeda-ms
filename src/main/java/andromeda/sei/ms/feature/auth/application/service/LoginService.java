package andromeda.sei.ms.feature.auth.application.service;

import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginResponse;
import andromeda.sei.ms.feature.auth.application.port.in.LoginUseCase;
import andromeda.sei.ms.feature.auth.application.port.out.UserAuthRepository;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.config.JwtProperties;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse execute(LoginRequest request) {
        User user = userAuthRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!user.active()) {
            throw new BusinessException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        List<String> permissions = roleRepository.findPermissionCodesByRoleId(user.roleId());
        String token = jwtService.generateToken(user.id(), user.organizationId(), permissions);

        return new LoginResponse(token, jwtProperties.expirationMs() / 1000);
    }
}
