package andromeda.sei.ms.feature.workitem.application.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public record WorkItemChildrenOutput(List<WorkItem> children, boolean truncated) {}
