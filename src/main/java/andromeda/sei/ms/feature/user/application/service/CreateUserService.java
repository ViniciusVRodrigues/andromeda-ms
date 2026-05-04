package andromeda.sei.ms.feature.user.application.service;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.CreateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import andromeda.sei.ms.feature.user.application.port.in.CreateUserUseCase;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse execute(CreateUserRequest request, UUID organizationId) {
        userRepository.findByEmailAndOrganizationId(request.email(), organizationId)
                .ifPresent(u -> { throw new BusinessException("User with email already exists in this organization"); });

        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        User user = User.create(organizationId, request.email(), passwordHash, request.name(), request.roleId());
        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
