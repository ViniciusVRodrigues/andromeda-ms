package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationSplitDocument {

    private String workItemId;
    private String workItemTitle;
    private BigDecimal percentage;
}
