package andromeda.sei.ms.feature.azure.domain;

public class HierarchyLevel {

    private String rawType;
    private Integer rank;

    public HierarchyLevel(String rawType, Integer rank) {
        this.rawType = rawType;
        this.rank = rank;
    }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
}
