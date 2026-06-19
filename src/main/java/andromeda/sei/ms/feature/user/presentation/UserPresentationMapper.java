package andromeda.sei.ms.feature.user.presentation;

import andromeda.sei.ms.feature.user.application.dto.CreateUserOutput;
import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.presentation.dto.CreateUserResponse;
import andromeda.sei.ms.feature.user.presentation.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserPresentationMapper {

    public UserResponse toResponse(UserOutput output) {
        return new UserResponse(
                output.id(),
                output.email(),
                output.name(),
                output.roleId(),
                output.seniority(),
                output.active(),
                output.createdAt(),
                output.azureId(),
                output.azureEmail()
        );
    }

    public CreateUserResponse toCreateResponse(CreateUserOutput output) {
        return new CreateUserResponse(
                output.id(),
                output.email(),
                output.roleId(),
                output.inviteToken(),
                output.createdAt()
        );
    }
}
