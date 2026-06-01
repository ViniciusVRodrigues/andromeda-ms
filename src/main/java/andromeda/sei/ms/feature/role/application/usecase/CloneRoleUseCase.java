package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.CloneRoleInput;
import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class CloneRoleUseCase {

    private final RoleRepository roleRepository;

    public RoleOutput execute(CloneRoleInput input, String organizationId) {
        Role source = roleRepository.findById(input.sourceRoleId())
                .orElseThrow(() -> new RoleNotFoundException(input.sourceRoleId()));

        Instant now = Instant.now();
        Role cloned = new Role(
                null,
                organizationId,
                input.newName(),
                false,
                source.getId(),
                new ArrayList<>(source.getPermissionCodes()),
                true,
                0L,
                now,
                now
        );

        Role saved = roleRepository.save(cloned);
        return RoleOutput.from(saved);
    }
}
