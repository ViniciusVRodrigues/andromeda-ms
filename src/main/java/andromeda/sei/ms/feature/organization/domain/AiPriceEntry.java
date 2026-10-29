package andromeda.sei.ms.feature.organization.domain;

import java.math.BigDecimal;

public class AiPriceEntry {

    private String model;
    private BigDecimal pricePerKInput;
    private BigDecimal pricePerKOutput;

    private BigDecimal pricePerKCacheRead;
    private BigDecimal pricePerKCacheWrite;

    public AiPriceEntry(String model, BigDecimal pricePerKInput, BigDecimal pricePerKOutput) {
        this(model, pricePerKInput, pricePerKOutput, null, null);
    }

    public AiPriceEntry(String model, BigDecimal pricePerKInput, BigDecimal pricePerKOutput,
                        BigDecimal pricePerKCacheRead, BigDecimal pricePerKCacheWrite) {
        this.model = model;
        this.pricePerKInput = pricePerKInput;
        this.pricePerKOutput = pricePerKOutput;
        this.pricePerKCacheRead = pricePerKCacheRead;
        this.pricePerKCacheWrite = pricePerKCacheWrite;
    }

    public String getModel() { return model; }
    public BigDecimal getPricePerKInput() { return pricePerKInput; }
    public BigDecimal getPricePerKOutput() { return pricePerKOutput; }
    public BigDecimal getPricePerKCacheRead() { return pricePerKCacheRead; }
    public BigDecimal getPricePerKCacheWrite() { return pricePerKCacheWrite; }
}
