package andromeda.sei.ms.feature.aiusage.presentation;

import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryByMonthUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.GetAiUsageSummaryUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetSprintItemsUseCase;
import andromeda.sei.ms.feature.allocation.presentation.dto.SprintItemResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryByMonthResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryGroupedResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.AiUsageSummaryResponse;
import andromeda.sei.ms.feature.aiusage.presentation.dto.ModelBreakdownResponse;
import andromeda.sei.ms.feature.connector.application.usecase.GetFluencyScoreUseCase;
import andromeda.sei.ms.feature.connector.presentation.dto.CategoryScoreResponse;
import andromeda.sei.ms.feature.connector.presentation.dto.FluencyScoreResponse;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

@Tag(name = "AI Usage", description = "AI usage metrics and fluency score endpoints")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiUsageController {

    private final GetAiUsageSummaryUseCase getAiUsageSummaryUseCase;
    private final GetFluencyScoreUseCase getFluencyScoreUseCase;
    private final GetAiUsageSummaryByMonthUseCase getAiUsageSummaryByMonthUseCase;
    private final GetSprintItemsUseCase getSprintItemsUseCase;
    private final UserRepository userRepository;
    private final RecalculateAiCostsUseCase recalculateAiCostsUseCase;

    @Operation(summary = "Get AI usage summary (date-range)", description = "Returns aggregate token/cost summary, optionally grouped by model. Pass ?userId only with DASHBOARD_TEAM permission to view another user's data.")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    @ApiResponse(responseCode = "403", description = "Not authorised to view another user's summary")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<?> getSummary(
            @AuthenticationPrincipal AndromedaUserDetails userDetails,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String groupBy) {

        String callerUserId = userDetails.getUserId();
        boolean hasDashboardTeam = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch("DASHBOARD_TEAM"::equals);
        if (userId != null && !userId.equals(callerUserId) && !hasDashboardTeam) {
            throw new ForbiddenOperationException("AI_USAGE_SUMMARY_FORBIDDEN",
                    "You are not authorised to view another user's usage summary");
        }
        String effectiveUserId = (userId != null && hasDashboardTeam) ? userId : callerUserId;
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

    @Operation(summary = "Get fluency score", description = "Returns the AI fluency score for the authenticated user or a team member")
    @ApiResponse(responseCode = "200", description = "Fluency score returned")
    @ApiResponse(responseCode = "404", description = "Fluency score not found")
    @ApiResponse(responseCode = "403", description = "Not authorised to view another user's fluency score")
    @GetMapping("/fluency-score")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<FluencyScoreResponse> getFluencyScore(
            @RequestParam(required = false) String userId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerUserId = auth.getName();
        boolean hasDashboardTeam = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("DASHBOARD_TEAM"::equals);

        if (userId != null && !userId.equals(callerUserId) && !hasDashboardTeam) {
            throw new ForbiddenOperationException("FLUENCY_SCORE_FORBIDDEN",
                    "You are not authorised to view another user's fluency score");
        }

        String effectiveUserId = (userId != null && hasDashboardTeam) ? userId : callerUserId;
        String orgId = TenantContextHolder.get();

        var score = getFluencyScoreUseCase.execute(orgId, effectiveUserId);

        List<CategoryScoreResponse> categories = score.getCategories().stream()
                .map(c -> new CategoryScoreResponse(c.getCategory(), c.getIcon(), c.getStage(), c.getTips()))
                .toList();

        return ResponseEntity.ok(new FluencyScoreResponse(
                score.getOverallStage(), score.getOverallLabel(), score.getComputedAt(), categories));
    }

    @Operation(summary = "Get AI usage summary by month", description = "Returns monthly aggregate with model breakdown")
    @ApiResponse(responseCode = "200", description = "Monthly summary returned")
    @GetMapping("/usage/summary")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<AiUsageSummaryByMonthResponse> getUsageSummaryByMonth(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String userId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerUserId = auth.getName();
        boolean hasDashboardTeam = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("DASHBOARD_TEAM"::equals);

        if (userId != null && !userId.equals(callerUserId) && !hasDashboardTeam) {
            throw new ForbiddenOperationException("AI_USAGE_SUMMARY_FORBIDDEN",
                    "You are not authorised to view another user's usage summary");
        }

        String effectiveUserId = (userId != null && hasDashboardTeam) ? userId : callerUserId;
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
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<List<SprintItemResponse>> getSprintItems(
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<SprintItemResponse> response = getSprintItemsUseCase
                .execute(orgId, user.getUserId())
                .stream()
                .map(i -> new SprintItemResponse(i.workItemId(), i.title(), i.sprintName()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get team members", description = "Returns all active users in the organisation (requires DASHBOARD_TEAM)")
    @ApiResponse(responseCode = "200", description = "Team members returned")
    @GetMapping("/team-members")
    @PreAuthorize("hasAuthority('DASHBOARD_TEAM')")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers() {
        String orgId = TenantContextHolder.get();
        List<TeamMemberResponse> members = userRepository.findAllByOrganizationId(orgId).stream()
                .filter(User::isActive)
                .map(u -> new TeamMemberResponse(u.getId(), u.getName()))
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

    public record TeamMemberResponse(String userId, String name) {}

    public record RecalculateCostsResponse(int updatedRecords) {}
}
