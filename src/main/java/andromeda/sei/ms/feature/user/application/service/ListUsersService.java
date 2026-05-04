package andromeda.sei.ms.feature.user.application.service;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import andromeda.sei.ms.feature.user.application.port.in.ListUsersUseCase;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListUsersService implements ListUsersUseCase {

    private final UserRepository userRepository;

    @Override
    public List<UserResponse> execute(UUID organizationId) {
        return userRepository.findAllByOrganizationId(organizationId).stream()
                .map(UserResponse::from).toList();
    }
}
