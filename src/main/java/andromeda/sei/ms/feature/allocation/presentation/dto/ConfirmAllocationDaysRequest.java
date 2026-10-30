package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code days}: 1 to 31 distinct dates, {@code yyyy-MM-dd}. */
public record ConfirmAllocationDaysRequest(
        @NotEmpty @Size(max = 31) List<String> days,
        Boolean includeReview
) {
    public boolean reviewIncluded() {
        return Boolean.TRUE.equals(includeReview);
    }

    @AssertTrue(message = "days must be distinct yyyy-MM-dd dates")
    public boolean isDaysWellFormed() {
        if (days == null) return true;
        Set<LocalDate> seen = new HashSet<>();
        for (String d : days) {
            if (d == null || d.length() != 10) return false;
            try {
                if (!seen.add(LocalDate.parse(d))) return false;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return true;
    }

    public List<LocalDate> parsedDays() {
        return days.stream().map(LocalDate::parse).toList();
    }
}
