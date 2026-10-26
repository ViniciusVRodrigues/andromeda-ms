package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationDeleteConfirmationMismatchException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.shared.application.security.OrganizationDeactivated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteOrganizationUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private OrganizationRepository organizationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeleteOrganizationUseCase useCase;

    @Test
    void should_deactivateOrganizationAndPublishEvent_when_slugMatches() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));

        useCase.execute(ORG, "acme");

        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().getUpdatedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        verify(eventPublisher).publishEvent(new OrganizationDeactivated(ORG));
    }

    @Test
    void should_throw422AndNotSave_when_slugDiffers() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));

        assertThatThrownBy(() -> useCase.execute(ORG, "other"))
                .isInstanceOf(OrganizationDeleteConfirmationMismatchException.class);
        verify(organizationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void should_throw422_when_slugDiffersOnlyByCase() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));

        assertThatThrownBy(() -> useCase.execute(ORG, "ACME"))
                .isInstanceOf(OrganizationDeleteConfirmationMismatchException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void should_throwNotFound_when_organizationDoesNotExist() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, "acme")).isInstanceOf(OrganizationNotFoundException.class);
        verify(organizationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static Organization organization() {
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        return new Organization(ORG, "Acme", "acme", null, null, null, null, null, true, 1L, past, past);
    }
}
