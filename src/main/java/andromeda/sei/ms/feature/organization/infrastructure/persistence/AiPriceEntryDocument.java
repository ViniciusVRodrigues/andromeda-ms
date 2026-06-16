package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPriceEntryDocument {

    private String model;
    private BigDecimal pricePerKInput;
    private BigDecimal pricePerKOutput;
}
