package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

public class TypeMapping {

    private String rawType;
    private WorkItem.ResolvedType resolvedType;
    /** Tipo elegivel a receber alocacao de uso de IA (tipos-recebem-ia). Padrao false. */
    private boolean receivesAi;

    public TypeMapping(String rawType, WorkItem.ResolvedType resolvedType) {
        this(rawType, resolvedType, false);
    }

    public TypeMapping(String rawType, WorkItem.ResolvedType resolvedType, boolean receivesAi) {
        this.rawType = rawType;
        this.resolvedType = resolvedType;
        this.receivesAi = receivesAi;
    }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public WorkItem.ResolvedType getResolvedType() { return resolvedType; }
    public void setResolvedType(WorkItem.ResolvedType resolvedType) { this.resolvedType = resolvedType; }

    public boolean isReceivesAi() { return receivesAi; }
    public void setReceivesAi(boolean receivesAi) { this.receivesAi = receivesAi; }
}
