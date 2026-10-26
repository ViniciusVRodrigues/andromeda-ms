package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.application.security.OrganizationDeactivated;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PermissionCacheInvalidationListenerTest {

    @Test
    void should_evictAll_when_organizationIsDeactivated() {
        EffectivePermissionService service = mock(EffectivePermissionService.class);

        new PermissionCacheInvalidationListener(service).on(new OrganizationDeactivated("org-1"));

        verify(service).evictAll();
    }
}
