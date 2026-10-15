package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.usecase.ExportUsageEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.exception.UsageExportRangeInvalidException;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsageExportControllerTest {

    private static final String URL = "/api/v1/usage-events/export";

    @Mock private ExportUsageEventsUseCase useCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UsageExportController(useCase, new RateLimiter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        TenantContextHolder.set("org-A");
        AndromedaUserDetails principal = AndromedaUserDetails.builder()
                .userId("admin-1").organizationId("org-A").permissions(List.of("USER_MANAGE", "ROLE_MANAGE")).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("USER_MANAGE"))));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private ExportUsageEventsUseCase.Export stubExport() throws Exception {
        ExportUsageEventsUseCase.Export export = new ExportUsageEventsUseCase.Export("org-A", "acme",
                LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"),
                Instant.parse("2026-10-19T03:00:00Z"), Instant.parse("2026-10-26T03:00:00Z"));
        when(useCase.prepare("org-A", LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))).thenReturn(export);
        doAnswer(inv -> {
            java.io.OutputStream out = inv.getArgument(1);
            out.write("﻿occurred_at\r\nrow\r\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(useCase).write(any(), any());
        return export;
    }

    @Test
    void should_requireBothUserManageAndRoleManage() throws Exception {
        var method = UsageExportController.class.getMethod("export", LocalDate.class, LocalDate.class,
                AndromedaUserDetails.class);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('USER_MANAGE') and hasAuthority('ROLE_MANAGE')");
    }

    @Test
    void should_streamTheCsv_withHeaders_andTenantOrg() throws Exception {
        stubExport();
        MvcResult started = mockMvc.perform(get(URL).param("from", "2026-10-19").param("to", "2026-10-25"))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"usage-events-acme-2026-10-19-2026-10-25.csv\""))
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("﻿occurred_at\r\nrow\r\n");
        verify(useCase).prepare("org-A", LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"));
    }

    @Test
    void should_answer422AsJson_beforeStreaming_whenRangeIsInvalid() throws Exception {
        when(useCase.prepare(anyString(), any(), any())).thenThrow(new UsageExportRangeInvalidException("x"));
        mockMvc.perform(get(URL).param("from", "2026-10-25").param("to", "2026-10-19"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("USAGE_EXPORT_RANGE_INVALID"));
    }

    @Test
    void should_answer400_whenDatesAreMissingOrMalformed() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-10-19"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get(URL).param("from", "ontem").param("to", "2026-10-25"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void should_answer429WithRetryAfter_onTheEleventhExport() throws Exception {
        stubExport();
        for (int i = 0; i < 10; i++) {
            MvcResult started = mockMvc.perform(get(URL).param("from", "2026-10-19").param("to", "2026-10-25"))
                    .andExpect(request().asyncStarted()).andReturn();
            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        }
        mockMvc.perform(get(URL).param("from", "2026-10-19").param("to", "2026-10-25"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("USAGE_EXPORT_RATE_LIMITED"));
    }

    @Test
    void should_downloadTheWholeCsv_withRealUseCaseOverFakeRepositories() throws Exception {
        var usageRepo = org.mockito.Mockito.mock(andromeda.sei.ms.feature.usage.domain.UsageEventRepository.class);
        var funnelRepo = org.mockito.Mockito.mock(andromeda.sei.ms.feature.usage.domain.FunnelEventRepository.class);
        var userRepo = org.mockito.Mockito.mock(andromeda.sei.ms.feature.user.domain.UserRepository.class);
        var orgRepo = org.mockito.Mockito.mock(andromeda.sei.ms.feature.organization.domain.OrganizationRepository.class);
        var orgDoc = org.mockito.Mockito.mock(andromeda.sei.ms.feature.organization.domain.Organization.class);
        when(orgDoc.getSlug()).thenReturn("acme");
        when(orgRepo.findById("org-A")).thenReturn(java.util.Optional.of(orgDoc));
        when(userRepo.findAllByOrganizationIdIncludingInactive("org-A")).thenReturn(List.of());
        Instant at = Instant.parse("2026-10-20T12:00:00Z");
        when(usageRepo.streamByOrganization(anyString(), any(), any())).thenReturn(java.util.stream.Stream.of(
                andromeda.sei.ms.feature.usage.domain.UsageEvent.restore("org-A", "u1",
                        andromeda.sei.ms.feature.usage.domain.UsageEventType.CLICK, "backlog", "v", "s", null,
                        "button", "=HYPERLINK(\"x\")", "main", null, null, "lg", null, null, null, at, at)));
        when(funnelRepo.streamByOrganization(anyString(), any(), any())).thenReturn(java.util.stream.Stream.empty());
        MockMvc real = MockMvcBuilders.standaloneSetup(new UsageExportController(
                        new ExportUsageEventsUseCase(usageRepo, funnelRepo, userRepo, orgRepo), new RateLimiter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

        MvcResult started = real.perform(get(URL).param("from", "2026-10-19").param("to", "2026-10-25"))
                .andExpect(request().asyncStarted()).andReturn();
        String body = real.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).startsWith("﻿occurred_at,received_at,user_id,email,type,");
        assertThat(body).contains("2026-10-20T12:00:00Z,2026-10-20T12:00:00Z,u1,,CLICK,backlog,,button,"
                + "\"'=HYPERLINK(\"\"x\"\")\",main");
        assertThat(body.split("[\r][\n]")).hasSize(2);
    }
}
