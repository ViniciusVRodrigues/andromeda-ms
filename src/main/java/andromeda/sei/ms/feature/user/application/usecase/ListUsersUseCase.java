package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListUsersUseCase {

    private final UserRepository userRepository;

    public List<UserOutput> execute(String organizationId) {
        return userRepository.findAllByOrganizationId(organizationId)
                .stream()
                .map(UserOutput::from)
                .toList();
    }
}
