package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

public class TypeMapping {

    private String rawType;
    private WorkItem.ResolvedType resolvedType;

    public TypeMapping(String rawType, WorkItem.ResolvedType resolvedType) {
        this.rawType = rawType;
        this.resolvedType = resolvedType;
    }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public WorkItem.ResolvedType getResolvedType() { return resolvedType; }
    public void setResolvedType(WorkItem.ResolvedType resolvedType) { this.resolvedType = resolvedType; }
}
