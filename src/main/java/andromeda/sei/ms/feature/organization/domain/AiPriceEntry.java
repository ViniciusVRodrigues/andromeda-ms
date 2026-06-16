package andromeda.sei.ms.feature.organization.domain;

import java.math.BigDecimal;

public class AiPriceEntry {

    private String model;
    private BigDecimal pricePerKInput;
    private BigDecimal pricePerKOutput;

    public AiPriceEntry(String model, BigDecimal pricePerKInput, BigDecimal pricePerKOutput) {
        this.model = model;
        this.pricePerKInput = pricePerKInput;
        this.pricePerKOutput = pricePerKOutput;
    }

    public String getModel() { return model; }
    public BigDecimal getPricePerKInput() { return pricePerKInput; }
    public BigDecimal getPricePerKOutput() { return pricePerKOutput; }
}
