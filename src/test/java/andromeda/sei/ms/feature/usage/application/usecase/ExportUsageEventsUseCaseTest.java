package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import andromeda.sei.ms.feature.usage.domain.exception.UsageExportRangeInvalidException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportUsageEventsUseCaseTest {

    @Mock private UsageEventRepository usageRepo;
    @Mock private FunnelEventRepository funnelRepo;
    @Mock private UserRepository userRepo;
    @Mock private OrganizationRepository orgRepo;

    private ExportUsageEventsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExportUsageEventsUseCase(usageRepo, funnelRepo, userRepo, orgRepo);
        Organization org = mock(Organization.class);
        lenient().when(org.getSlug()).thenReturn("acme");
        lenient().when(orgRepo.findById("org-A")).thenReturn(Optional.of(org));
    }

    private static User user(String id, String email) {
        User u = mock(User.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.getEmail()).thenReturn(email);
        return u;
    }

    private static UsageEvent click(String userId, String at, String label) {
        return UsageEvent.restore("org-A", userId, UsageEventType.CLICK, "backlog", "v", "s", null, "button",
                label, "main", null, null, "lg", null, null, null, Instant.parse(at), Instant.parse(at));
    }

    private static FunnelEvent funnel(String at, String orgId, String userId, FunnelStage stage) {
        return FunnelEvent.restore("fid", FunnelFlow.SIGNUP, stage, "pessoal",
                stage == FunnelStage.STEP_TIME ? 4000L : null, null, "lg", Instant.parse(at), orgId, userId, null,
                Instant.parse(at));
    }

    private static ExportUsageEventsUseCase.Export week(ExportUsageEventsUseCase useCase) {
        return useCase.prepare("org-A", LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"));
    }

    private String run(ExportUsageEventsUseCase.Export export) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        useCase.write(export, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void should_useBrtBoundaries_inclusiveOfTo() {
        ExportUsageEventsUseCase.Export e = week(useCase);
        assertThat(e.start()).isEqualTo(Instant.parse("2026-10-19T03:00:00Z"));
        assertThat(e.end()).isEqualTo(Instant.parse("2026-10-26T03:00:00Z"));
        assertThat(e.orgSlug()).isEqualTo("acme");
    }

    @Test
    void should_rejectToBeforeFrom_andMoreThan366Days() {
        assertThatThrownBy(() -> useCase.prepare("org-A", LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19")))
                .isInstanceOf(UsageExportRangeInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "USAGE_EXPORT_RANGE_INVALID");
        assertThatThrownBy(() -> useCase.prepare("org-A", LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-02")))
                .isInstanceOf(UsageExportRangeInvalidException.class);
        assertThat(useCase.prepare("org-A", LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"))).isNotNull();
    }

    @Test
    void should_queryOnlyTheTenantOrgAndPeriod_andJoinInactiveUsersEmail() throws IOException {
        List<User> users = List.of(user("u1", "ativo@x.com"), user("u2", "inativo@x.com"));
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(users);
        when(usageRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.of(
                click("u2", "2026-10-20T12:00:00Z", "Salvar")));
        when(funnelRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.empty());

        String csv = run(week(useCase));

        verify(usageRepo).streamByOrganization("org-A", Instant.parse("2026-10-19T03:00:00Z"),
                Instant.parse("2026-10-26T03:00:00Z"));
        verify(funnelRepo).streamByOrganization("org-A", Instant.parse("2026-10-19T03:00:00Z"),
                Instant.parse("2026-10-26T03:00:00Z"));
        assertThat(csv).startsWith("﻿occurred_at,");
        assertThat(csv).contains(
                "2026-10-20T12:00:00Z,2026-10-20T12:00:00Z,u2,inativo@x.com,CLICK,backlog,,button,Salvar,main");
    }

    @Test
    void should_leaveEmailEmpty_whenUserIsGone() throws IOException {
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(List.of());
        when(usageRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.of(
                click("ghost", "2026-10-20T12:00:00Z", "x")));
        when(funnelRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.empty());
        assertThat(run(week(useCase))).contains(",ghost,,CLICK,");
    }

    @Test
    void should_mergeByOccurredAt_marksFunnelAsFUNNEL_andSkipsAnonymous() throws IOException {
        List<User> users = List.of(user("u1", "a@x.com"));
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(users);
        when(usageRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.of(
                click("u1", "2026-10-20T10:00:00Z", "A"), click("u1", "2026-10-20T12:00:00Z", "C")));
        when(funnelRepo.streamByOrganization(any(), any(), any())).thenReturn(Stream.of(
                funnel("2026-10-20T09:00:00Z", "org-A", "u1", FunnelStage.OPEN),
                funnel("2026-10-20T11:00:00Z", "org-A", "u1", FunnelStage.STEP_TIME),
                funnel("2026-10-20T11:30:00Z", null, null, FunnelStage.OPEN),
                funnel("2026-10-20T13:00:00Z", "org-A", "u1", FunnelStage.COMPLETE)));

        List<String> lines = List.of(run(week(useCase)).split("\r\n"));

        assertThat(lines).hasSize(1 + 5);
        assertThat(lines.subList(1, 6)).extracting(l -> l.substring(0, 20)).containsExactly(
                "2026-10-20T09:00:00Z", "2026-10-20T10:00:00Z", "2026-10-20T11:00:00Z",
                "2026-10-20T12:00:00Z", "2026-10-20T13:00:00Z");
        assertThat(lines.get(1)).contains(",u1,a@x.com,FUNNEL,").endsWith(",signup,OPEN,pessoal,fid");
        assertThat(lines.get(3)).contains(",FUNNEL,,4000,").endsWith(",signup,STEP_TIME,pessoal,fid");
    }

    @Test
    void should_closeBothStreams_afterSuccess() throws IOException {
        AtomicBoolean usageClosed = new AtomicBoolean();
        AtomicBoolean funnelClosed = new AtomicBoolean();
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(List.of());
        when(usageRepo.streamByOrganization(any(), any(), any())).thenReturn(
                Stream.of(click("u1", "2026-10-20T10:00:00Z", "A")).onClose(() -> usageClosed.set(true)));
        when(funnelRepo.streamByOrganization(any(), any(), any())).thenReturn(
                Stream.<FunnelEvent>empty().onClose(() -> funnelClosed.set(true)));
        run(week(useCase));
        assertThat(usageClosed).isTrue();
        assertThat(funnelClosed).isTrue();
    }

    @Test
    void should_closeBothStreams_whenReadingFailsMidway() {
        AtomicBoolean usageClosed = new AtomicBoolean();
        AtomicBoolean funnelClosed = new AtomicBoolean();
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(List.of());
        when(usageRepo.streamByOrganization(any(), any(), any())).thenReturn(
                Stream.of(1, 2).<UsageEvent>map(i -> {
                    if (i == 2) throw new IllegalStateException("cursor died");
                    return click("u1", "2026-10-20T10:00:00Z", "A");
                }).onClose(() -> usageClosed.set(true)));
        when(funnelRepo.streamByOrganization(any(), any(), any())).thenReturn(
                Stream.<FunnelEvent>empty().onClose(() -> funnelClosed.set(true)));
        assertThatThrownBy(() -> run(week(useCase))).isInstanceOf(IllegalStateException.class);
        assertThat(usageClosed).isTrue();
        assertThat(funnelClosed).isTrue();
    }
}
