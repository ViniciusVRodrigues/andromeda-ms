package andromeda.sei.ms.feature.azure.domain;

import java.util.List;

public class WiqlFilter {

    private List<String> workItemTypes;
    private String areaPath;
    private String iterationPath;

    public WiqlFilter(List<String> workItemTypes, String areaPath, String iterationPath) {
        this.workItemTypes = workItemTypes;
        this.areaPath = areaPath;
        this.iterationPath = iterationPath;
    }

    public List<String> getWorkItemTypes() { return workItemTypes; }
    public void setWorkItemTypes(List<String> workItemTypes) { this.workItemTypes = workItemTypes; }

    public String getAreaPath() { return areaPath; }
    public void setAreaPath(String areaPath) { this.areaPath = areaPath; }

    public String getIterationPath() { return iterationPath; }
    public void setIterationPath(String iterationPath) { this.iterationPath = iterationPath; }
}
