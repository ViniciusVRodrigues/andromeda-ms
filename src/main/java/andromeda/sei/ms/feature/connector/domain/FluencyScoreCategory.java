package andromeda.sei.ms.feature.connector.domain;

import java.util.List;

public class FluencyScoreCategory {

    private String category;
    private String icon;
    private int stage;
    private List<String> tips;

    public FluencyScoreCategory(String category, String icon, int stage, List<String> tips) {
        this.category = category;
        this.icon = icon;
        this.stage = stage;
        this.tips = tips;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }

    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) { this.tips = tips; }
}
