package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Up to 10 patterns (null or empty previews the defaults) and up to 20 sample branches; beyond that, 400. */
public record AgentBranchPreviewRequest(@Size(max = 10) List<String> branchPatterns,
                                        @NotNull @Size(max = 20) List<String> branches) {}
