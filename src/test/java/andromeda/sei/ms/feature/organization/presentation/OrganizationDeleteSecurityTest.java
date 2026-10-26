package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.DeleteOrganizationUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.DeleteOrganizationRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/** {@code DELETE /organizations/me} really requires {@code ORGANIZATION_DELETE} through method security. */
class OrganizationDeleteSecurityTest {

    private static final DeleteOrganizationUseCase USE_CASE = mock(DeleteOrganizationUseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean OrganizationController organizationController() {
            return new OrganizationController(null, null, null, null, USE_CASE, null, null, null, null, null);
        }
    }

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stopContext() {
        context.close();
    }

    @BeforeEach
    void setUp() {
        reset(USE_CASE);
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void should_deny403_when_callerLacksOrganizationDelete() {
        authenticateWith("USER_MANAGE", "PROJECT_MANAGE");

        assertThatThrownBy(() -> controller().deleteMe(new DeleteOrganizationRequest("acme")))
                .isInstanceOf(AccessDeniedException.class);
        verify(USE_CASE, never()).execute(any(), any(), any());
    }

    @Test
    void should_callUseCase_when_callerHoldsOrganizationDelete() {
        authenticateWith("ORGANIZATION_DELETE");

        controller().deleteMe(new DeleteOrganizationRequest("acme"));

        verify(USE_CASE).execute("org-1", "user-a", "acme");
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-a", null, AuthorityUtils.createAuthorityList(authorities)));
    }

    private static OrganizationController controller() {
        return context.getBean(OrganizationController.class);
    }
}
