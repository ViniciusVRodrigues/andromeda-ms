package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationInput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSlugConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrganizationUseCaseTest {

    @Mock
    private OrganizationRepository repository;

    @InjectMocks
    private CreateOrganizationUseCase useCase;

    @Test
    void shouldCreateOrganizationWithGeneratedSlug() {
        var now = Instant.now();
        var saved = new Organization("org-1", "Minha Empresa", "minha-empresa", true, 0L, now, now);

        when(repository.existsBySlug("minha-empresa")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);

        var output = useCase.execute(new CreateOrganizationInput("Minha Empresa"));

        assertThat(output.id()).isEqualTo("org-1");
        assertThat(output.slug()).isEqualTo("minha-empresa");
        assertThat(output.name()).isEqualTo("Minha Empresa");
        verify(repository).save(any(Organization.class));
    }

    @Test
    void shouldThrowConflictWhenSlugAlreadyExists() {
        when(repository.existsBySlug("minha-empresa")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new CreateOrganizationInput("Minha Empresa")))
                .isInstanceOf(OrganizationSlugConflictException.class)
                .hasMessageContaining("minha-empresa");
    }

    @Test
    void shouldGenerateSlugFromSpecialCharacterName() {
        var now = Instant.now();
        var saved = new Organization("org-2", "Acme & Co.", "acme-and-co", true, 0L, now, now);

        when(repository.existsBySlug("acme-and-co")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);

        var output = useCase.execute(new CreateOrganizationInput("Acme & Co."));

        assertThat(output.slug()).isEqualTo("acme-and-co");
    }
}
