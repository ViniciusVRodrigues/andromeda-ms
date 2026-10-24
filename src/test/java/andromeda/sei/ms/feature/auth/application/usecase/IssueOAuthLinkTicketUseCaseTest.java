package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketInput;
import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketOutput;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicketRepository;
import andromeda.sei.ms.feature.auth.domain.exception.OAuthLinkRequestInvalidException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueOAuthLinkTicketUseCaseTest {

    @Mock OAuthLinkTicketRepository tickets;
    @InjectMocks IssueOAuthLinkTicketUseCase useCase;

    private IssueOAuthLinkTicketInput input(OAuthLinkPurpose p, String url, List<String> perms) {
        return new IssueOAuthLinkTicketInput("user-1", "org-1", perms, p, url);
    }

    @Test
    void should_storeOnlyTheHash_andReturnTheRawTicket() {
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));

        IssueOAuthLinkTicketOutput out = useCase.execute(input(OAuthLinkPurpose.GITHUB_PROFILE, null, List.of()));

        ArgumentCaptor<OAuthLinkTicket> saved = ArgumentCaptor.forClass(OAuthLinkTicket.class);
        verify(tickets).save(saved.capture());
        assertThat(out.ticket()).hasSize(43);
        assertThat(out.expiresIn()).isEqualTo(60);
        assertThat(saved.getValue().getTicketHash())
                .isEqualTo(OAuthLinkTicket.hash(out.ticket()))
                .isNotEqualTo(out.ticket());
        assertThat(saved.getValue().getUserId()).isEqualTo("user-1");
        assertThat(saved.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(saved.getValue().getExpiresAt())
                .isEqualTo(saved.getValue().getCreatedAt().plusSeconds(60));
    }

    @Test
    void should_issueDifferentTickets() {
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));
        var a = useCase.execute(input(OAuthLinkPurpose.GITHUB_PROFILE, null, List.of()));
        var b = useCase.execute(input(OAuthLinkPurpose.GITHUB_PROFILE, null, List.of()));
        assertThat(a.ticket()).isNotEqualTo(b.ticket());
    }

    @Test
    void should_forbidAzureConnect_withoutAzureConfigure() {
        assertThatThrownBy(() -> useCase.execute(input(OAuthLinkPurpose.AZURE_ORG_CONNECT,
                "https://dev.azure.com/acme", List.of("AZURE_READ"))))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(tickets, never()).save(any());
    }

    @Test
    void should_rejectInvalidAzureOrgUrl() {
        assertThatThrownBy(() -> useCase.execute(input(OAuthLinkPurpose.AZURE_ORG_CONNECT,
                "https://evil.example.com/acme", List.of("AZURE_CONFIGURE"))))
                .isInstanceOf(OAuthLinkRequestInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "AZURE_ORG_URL_INVALID");
        verify(tickets, never()).save(any());
    }

    @Test
    void should_issueAzureTicket_withNormalizedUrl() {
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(input(OAuthLinkPurpose.AZURE_ORG_CONNECT,
                "https://dev.azure.com/acme/", List.of("AZURE_CONFIGURE")));

        ArgumentCaptor<OAuthLinkTicket> saved = ArgumentCaptor.forClass(OAuthLinkTicket.class);
        verify(tickets).save(saved.capture());
        assertThat(saved.getValue().getAzureOrgUrl()).isEqualTo("https://dev.azure.com/acme");
        assertThat(saved.getValue().getPurpose()).isEqualTo(OAuthLinkPurpose.AZURE_ORG_CONNECT);
    }
}
