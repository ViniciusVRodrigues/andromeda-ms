package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleDuplicateNameException;
import andromeda.sei.ms.feature.role.domain.exception.RoleInvalidNameException;

/** Name rules shared by the use cases that create a role (create and clone). */
final class RoleNames {

    static final int MAX_LENGTH = 60;

    private RoleNames() {
    }

    /** Trims, validates and checks uniqueness (org roles, soft-deleted ones included, and defaults). */
    static String requireAvailable(String rawName, String organizationId, RoleRepository roleRepository) {
        if (rawName == null || rawName.isBlank() || rawName.trim().length() > MAX_LENGTH) {
            throw new RoleInvalidNameException();
        }
        String name = rawName.trim();
        if (roleRepository.findByNameAndOrganizationId(name, organizationId).isPresent()
                || roleRepository.findGlobalByName(name).isPresent()) {
            throw new RoleDuplicateNameException(name);
        }
        return name;
    }
}
