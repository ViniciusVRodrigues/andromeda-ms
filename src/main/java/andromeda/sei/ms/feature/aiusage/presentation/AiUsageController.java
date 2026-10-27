package andromeda.sei.ms.feature.aiusage.presentation;

import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryByMonthUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetDailyAiUsageUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryUseCase;
import andromeda.sei.ms.feature.aiusage.application.dto.UnpricedModelOutput;
import andromeda.sei.ms.feature.aiusage.application.usecase.ListUnpricedModelsUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetSprintItemsUseCase;
import andromeda.sei.ms.feature.allocation.presentation.dto.SprintItemResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryByMonthResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryGroupedResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.ModelBreakdownResponse;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Tag(name = "AI Usage", description = "AI usage metrics endpoints")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiUsageController {

    private final GetAiUsageSummaryUseCase getAiUsageSummaryUseCase;
    private final GetAiUsageSummaryByMonthUseCase getAiUsageSummaryByMonthUseCase;
    private final GetSprintItemsUseCase getSprintItemsUseCase;
    private final UserRepository userRepository;
    private final RecalculateAiCostsUseCase recalculateAiCostsUseCase;

    private final GetDailyAiUsageUseCase getDailyAiUsageUseCase;
    private final CurrentUserPermissions currentUserPermissions;
    private final ListUnpricedModelsUseCase listUnpricedModelsUseCase;

    /**
     * Own usage (AI_READ_OWN, ORG_ONLY) or other members' usage (AI_READ_ALL, PROJECT_SCOPABLE — held
     * on at least one project; each request for another user is then checked against that user's
     * projects by {@link CurrentUserPermissions#canReadUserData}).
     */
    static final String AI_READ = "hasAuthority('AI_READ_OWN') or @perm.onAnyProject('AI_READ_ALL')";

    @Operation(summary = "Consumo diário de IA no mês")
    @ApiResponse(responseCode = "200", description = "Consumo diário retornado")
    @GetMapping("/usage/daily")
    @PreAuthorize(AI_READ)
    public ResponseEntity<?> dailyUsage(@RequestParam(required = false) String month,
                                       @RequestParam(required = false) String userId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (userId != null && !currentUserPermissions.canReadUserData(userId)) {
            throw new ForbiddenOperationException("AI_USAGE_SUMMARY_FORBIDDEN", "Sem permissão para consultar outro usuário");
        }
        return ResponseEntity.ok(getDailyAiUsageUseCase.execute(TenantContextHolder.get(),
                userId == null ? auth.getName() : userId, month == null ? YearMonth.now() : YearMonth.parse(month)));
    }

    @Operation(summary = "Get AI usage summary (date-range)", description = "Returns aggregate token/cost summary, optionally grouped by model. Pass ?userId only with DASHBOARD_TEAM permission to view another user's data.")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    @ApiResponse(responseCode = "403", description = "Not authorised to view another user's summary")
    @GetMapping("/summary")
    @PreAuthorize(AI_READ)
    public ResponseEntity<?> getSummary(
            @AuthenticationPrincipal AndromedaUserDetails userDetails,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String groupBy) {

        String callerUserId = userDetails.getUserId();
        if (userId != null && !currentUserPermissions.canReadUserData(userId)) {
            throw new ForbiddenOperationException("AI_USAGE_SUMMARY_FORBIDDEN",
                    "You are not authorised to view another user's usage summary");
        }
        String effectiveUserId = userId != null ? userId : callerUserId;
        String orgId = TenantContextHolder.get();

        var output = getAiUsageSummaryUseCase.execute(
                effectiveUserId, null, false, orgId, startDate, endDate, model, groupBy);

        if (output.groupedByModel() != null) {
            List<AiUsageSummaryGroupedResponse> response = output.groupedByModel().stream()
                    .map(g -> new AiUsageSummaryGroupedResponse(
                            g.model(), g.totalInputTokens(), g.totalOutputTokens(),
                            g.totalInteractions(), g.totalCostUsd()))
                    .toList();
            return ResponseEntity.ok(response);
        }

        var s = output.summary();
        return ResponseEntity.ok(new AiUsageSummaryResponse(
                s.totalInputTokens(), s.totalOutputTokens(), s.totalInteractions(), s.totalCostUsd()));
    }

    @Operation(summary = "Get AI usage summary by month", description = "Returns monthly aggregate with model breakdown")
    @ApiResponse(responseCode = "200", description = "Monthly summary returned")
    @GetMapping("/usage/summary")
    @PreAuthorize(AI_READ)
    public ResponseEntity<AiUsageSummaryByMonthResponse> getUsageSummaryByMonth(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String userId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerUserId = auth.getName();
        if (userId != null && !currentUserPermissions.canReadUserData(userId)) {
            throw new ForbiddenOperationException("AI_USAGE_SUMMARY_FORBIDDEN",
                    "You are not authorised to view another user's usage summary");
        }

        String effectiveUserId = userId != null ? userId : callerUserId;
        String orgId = TenantContextHolder.get();

        YearMonth yearMonth = (month != null) ? YearMonth.parse(month) : YearMonth.now();

        var output = getAiUsageSummaryByMonthUseCase.execute(orgId, effectiveUserId, yearMonth);

        List<ModelBreakdownResponse> breakdown = output.modelBreakdown().stream()
                .map(m -> new ModelBreakdownResponse(m.model(), m.percentage()))
                .toList();

        return ResponseEntity.ok(new AiUsageSummaryByMonthResponse(
                output.month().toString(),
                output.totalInputTokens(),
                output.totalOutputTokens(),
                output.totalInteractions(),
                output.totalCostUsd(),
                breakdown));
    }

    @Operation(summary = "Get sprint items for allocation", description = "Returns work items in current sprints assigned to the authenticated user")
    @ApiResponse(responseCode = "200", description = "Sprint items returned")
    @GetMapping("/sprint-items")
    @PreAuthorize(AI_READ)
    public ResponseEntity<List<SprintItemResponse>> getSprintItems(
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<SprintItemResponse> response = getSprintItemsUseCase
                .execute(orgId, user.getUserId())
                .stream()
                .map(i -> new SprintItemResponse(
                        i.workItemId(), i.title(), i.sprintName(), i.type(), i.externalId(), i.storyPoints(),
                        i.complexity(), i.effort()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get team members", description = "Returns all active users in the organisation (requires DASHBOARD_TEAM)")
    @ApiResponse(responseCode = "200", description = "Team members returned")
    @GetMapping("/team-members")
    @PreAuthorize("@perm.onAnyProject('DASHBOARD_TEAM') or @perm.onAnyProject('AI_READ_ALL')")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers() {
        String orgId = TenantContextHolder.get();
        // Only members of projects where the caller holds DASHBOARD_TEAM/AI_READ_ALL (6.4/7.3).
        Optional<Set<String>> visible = currentUserPermissions.visibleUserIds();
        List<TeamMemberResponse> members = userRepository.findAllByOrganizationId(orgId).stream()
                .filter(User::isActive)
                .filter(u -> visible.isEmpty() || visible.get().contains(u.getId()))
                /* email acompanha o name para que a UI tenha um rótulo mesmo quando o
                 * usuário ainda não preencheu o nome (convite aceito, cadastro incompleto) —
                 * sem ele a opção aparecia em branco no seletor. */
                .map(u -> new TeamMemberResponse(u.getId(), u.getName(), u.getEmail()))
                .toList();
        return ResponseEntity.ok(members);
    }

    @Operation(summary = "Recalculate AI costs", description = "Backfills costUsd on all AI usage records for the org using current price table. Requires PRICE_TABLE_MANAGE.")
    @ApiResponse(responseCode = "200", description = "Number of records updated")
    @PostMapping("/recalculate-costs")
    @PreAuthorize("hasAuthority('PRICE_TABLE_MANAGE')")
    public ResponseEntity<RecalculateCostsResponse> recalculateCosts() {
        String orgId = TenantContextHolder.get();
        int updated = recalculateAiCostsUseCase.execute(orgId);
        return ResponseEntity.ok(new RecalculateCostsResponse(updated));
    }

    @Operation(summary = "Models without a price", description = "Models used in the period (default all recorded usage) with no row in the price table. Requires PRICE_TABLE_MANAGE.")
    @ApiResponse(responseCode = "200", description = "Unpriced models, most output tokens first")
    @ApiResponse(responseCode = "400", description = "AI_USAGE_PERIOD_INVALID")
    @GetMapping("/unpriced-models")
    @PreAuthorize("hasAuthority('PRICE_TABLE_MANAGE')")
    public ResponseEntity<List<UnpricedModelOutput>> unpricedModels(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(listUnpricedModelsUseCase.execute(TenantContextHolder.get(), from, to));
    }

    /**
     * {@code id} e não {@code userId}: é o nome que todo o resto da API usa para a
     * identidade de um recurso (ver /v1/users, /v1/roles), e é o que o front lê. A
     * divergência anterior fazia o seletor "Visualizar como" enviar o nome do usuário
     * como se fosse id, quebrando summary, pending e history de uma vez.
     */
    public record TeamMemberResponse(String id, String name, String email) {}

    public record RecalculateCostsResponse(int updatedRecords) {}
}
