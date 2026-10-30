package andromeda.sei.ms.feature.azure.domain;

import java.util.List;

public class WiqlFilter {

    private List<String> workItemTypes;
    private List<String> excludedWorkItemTypes;
    private String areaPath;
    private String iterationPath;

    public WiqlFilter(List<String> workItemTypes, String areaPath, String iterationPath) {
        this(workItemTypes, List.of(), areaPath, iterationPath);
    }

    public WiqlFilter(List<String> workItemTypes, List<String> excludedWorkItemTypes,
                      String areaPath, String iterationPath) {
        this.workItemTypes = workItemTypes;
        this.excludedWorkItemTypes = excludedWorkItemTypes == null ? List.of() : excludedWorkItemTypes;
        this.areaPath = areaPath;
        this.iterationPath = iterationPath;
    }

    public List<String> getWorkItemTypes() { return workItemTypes; }
    public void setWorkItemTypes(List<String> workItemTypes) { this.workItemTypes = workItemTypes; }

    public List<String> getExcludedWorkItemTypes() {
        return excludedWorkItemTypes == null ? List.of() : excludedWorkItemTypes;
    }
    public void setExcludedWorkItemTypes(List<String> excludedWorkItemTypes) {
        this.excludedWorkItemTypes = excludedWorkItemTypes == null ? List.of() : excludedWorkItemTypes;
    }

    public String getAreaPath() { return areaPath; }
    public void setAreaPath(String areaPath) { this.areaPath = areaPath; }

    public String getIterationPath() { return iterationPath; }
    public void setIterationPath(String iterationPath) { this.iterationPath = iterationPath; }
}
