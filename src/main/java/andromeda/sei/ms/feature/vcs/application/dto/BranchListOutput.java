package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;

import java.util.List;

/** Branches of a repository: default first, then by name; {@code truncated} when the provider has more than were read. */
public record BranchListOutput(List<VcsBranch> branches, int total, boolean truncated) {}
