package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AllocationApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** The person's "Fora de entrega" budget of a month (uso-ia-fora-de-entrega, design 2.2). */
@Service
@RequiredArgsConstructor
public class GetOutOfDeliveryBudgetUseCase {

    public record Output(YearMonth month, int capPercent, BigDecimal baseUsd, String baseMonth, BigDecimal capUsd,
                         BigDecimal usedUsd, BigDecimal awaitingUsd) {
    }

    private final AllocationApprovalService approvalService;

    public Output execute(String organizationId, String userId, YearMonth month) {
        // The fallback base reads the month itself up to today, or to its last day when it is over.
        LocalDate upTo = LocalDate.now().isBefore(month.atEndOfMonth()) ? LocalDate.now() : month.atEndOfMonth();
        AllocationApprovalService.Budget budget = approvalService.budget(organizationId, userId, month, upTo, List.of());
        return new Output(month, budget.capPercent(), money(budget.baseUsd()), budget.baseMonth(),
                money(budget.capUsd()), money(budget.validatedUsd().add(budget.awaitingUsd())), money(budget.awaitingUsd()));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
