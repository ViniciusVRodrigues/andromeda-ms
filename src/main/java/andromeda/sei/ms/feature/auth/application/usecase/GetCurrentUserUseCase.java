package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserInput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidCredentialsException;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public GetCurrentUserOutput execute(GetCurrentUserInput input) {
        User user = userRepository.findByIdAndOrganizationId(input.userId(), input.organizationId())
                .orElseThrow(InvalidCredentialsException::new);

        Organization org = organizationRepository.findById(input.organizationId())
                .orElseThrow(InvalidCredentialsException::new);

        return new GetCurrentUserOutput(
                user.getId(),
                user.getName(),
                user.getEmail(),
                org.getId(),
                org.getName(),
                org.getSlug(),
                input.permissionCodes()
        );
    }
}
