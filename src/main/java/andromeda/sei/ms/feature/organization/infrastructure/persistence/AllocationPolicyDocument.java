package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Subdocumento embutido em {@code organizations.allocationPolicy}; ausente = padrao (15). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllocationPolicyDocument {

    private Integer outOfDeliveryCapPercent;
}
