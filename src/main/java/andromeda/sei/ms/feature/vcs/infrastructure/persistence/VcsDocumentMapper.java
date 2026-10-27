package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import org.springframework.stereotype.Component;

/** Domain ↔ document mapping of the five {@code vcs_*} collections. */
@Component
public class VcsDocumentMapper {

    public VcsRepository toDomain(VcsRepositoryDocument doc) {
        if (doc == null) return null;
        VcsRepository repo = new VcsRepository(doc.getId(), doc.getOrganizationId(), doc.getProvider(),
                doc.getExternalRepoId(), doc.getDisplayName());
        repo.setExternalProjectId(doc.getExternalProjectId());
        repo.setDefaultBranch(doc.getDefaultBranch());
        repo.setExternalUpdatedAt(doc.getExternalUpdatedAt());
        repo.setActive(doc.isActive());
        repo.setCreatedAt(doc.getCreatedAt());
        repo.setUpdatedAt(doc.getUpdatedAt());
        repo.setVersion(doc.getVersion());
        return repo;
    }

    public VcsRepositoryDocument toDocument(VcsRepository repo) {
        return VcsRepositoryDocument.builder()
                .id(repo.getId())
                .organizationId(repo.getOrganizationId())
                .provider(repo.getProvider())
                .externalRepoId(repo.getExternalRepoId())
                .externalProjectId(repo.getExternalProjectId())
                .displayName(repo.getDisplayName())
                .defaultBranch(repo.getDefaultBranch())
                .externalUpdatedAt(repo.getExternalUpdatedAt())
                .active(repo.isActive())
                .createdAt(repo.getCreatedAt())
                .updatedAt(repo.getUpdatedAt())
                .version(repo.getVersion())
                .build();
    }

    public VcsRepoBinding toDomain(VcsRepoBindingDocument doc) {
        if (doc == null) return null;
        VcsRepoBinding binding = new VcsRepoBinding(doc.getId(), doc.getOrganizationId(), doc.getProjectId(),
                doc.getRepositoryId(), doc.getProvider(), doc.isSyncEnabled());
        binding.setLastSyncAt(doc.getLastSyncAt());
        binding.setActive(doc.isActive());
        binding.setCreatedAt(doc.getCreatedAt());
        binding.setUpdatedAt(doc.getUpdatedAt());
        binding.setVersion(doc.getVersion());
        binding.setBranches(doc.getBranches());
        binding.setProductionBranch(doc.getProductionBranch());
        binding.setFilterPullRequestsByBranch(doc.isFilterPullRequestsByBranch());
        binding.setMissingBranches(doc.getMissingBranches());
        binding.setSyncState(doc.getSyncState());
        binding.setSyncStateAt(doc.getSyncStateAt());
        return binding;
    }

    public VcsRepoBindingDocument toDocument(VcsRepoBinding binding) {
        return VcsRepoBindingDocument.builder()
                .id(binding.getId())
                .organizationId(binding.getOrganizationId())
                .projectId(binding.getProjectId())
                .repositoryId(binding.getRepositoryId())
                .provider(binding.getProvider())
                .syncEnabled(binding.isSyncEnabled())
                .lastSyncAt(binding.getLastSyncAt())
                .active(binding.isActive())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .version(binding.getVersion())
                .branches(binding.getBranches())
                .productionBranch(binding.getProductionBranch())
                .filterPullRequestsByBranch(binding.isFilterPullRequestsByBranch())
                .missingBranches(binding.getMissingBranches())
                .syncState(binding.getSyncState())
                .syncStateAt(binding.getSyncStateAt())
                .build();
    }

    public VcsPullRequest toDomain(VcsPullRequestDocument doc) {
        if (doc == null) return null;
        VcsPullRequest pr = new VcsPullRequest(doc.getId(), doc.getOrganizationId(), doc.getRepositoryId(),
                doc.getProvider(), doc.getExternalId(), doc.getNumber(), doc.getTitle(), doc.getStatus());
        pr.setAuthorLogin(doc.getAuthorLogin());
        pr.setAuthorExternalId(doc.getAuthorExternalId());
        pr.setAuthorUserId(doc.getAuthorUserId());
        pr.setCreatedAt(doc.getCreatedAt());
        pr.setMergedAt(doc.getMergedAt());
        pr.setClosedAt(doc.getClosedAt());
        pr.setExternalUpdatedAt(doc.getExternalUpdatedAt());
        pr.setCycleTimeHours(doc.getCycleTimeHours());
        pr.setTimeToFirstReviewHours(doc.getTimeToFirstReviewHours());
        pr.setTimeToFirstApprovalHours(doc.getTimeToFirstApprovalHours());
        pr.setLinkedWorkItemExternalId(doc.getLinkedWorkItemExternalId());
        pr.setDraft(doc.getDraft());
        pr.setBaseBranch(doc.getBaseBranch());
        pr.setSourceBranch(doc.getSourceBranch());
        pr.setLabels(doc.getLabels());
        pr.setEarliestCommitAt(doc.getEarliestCommitAt());
        pr.setActive(doc.isActive());
        pr.setUpdatedAt(doc.getUpdatedAt());
        pr.setVersion(doc.getVersion());
        return pr;
    }

    public VcsPullRequestDocument toDocument(VcsPullRequest pr) {
        return VcsPullRequestDocument.builder()
                .id(pr.getId())
                .organizationId(pr.getOrganizationId())
                .repositoryId(pr.getRepositoryId())
                .provider(pr.getProvider())
                .externalId(pr.getExternalId())
                .number(pr.getNumber())
                .title(pr.getTitle())
                .status(pr.getStatus())
                .authorLogin(pr.getAuthorLogin())
                .authorExternalId(pr.getAuthorExternalId())
                .authorUserId(pr.getAuthorUserId())
                .createdAt(pr.getCreatedAt())
                .mergedAt(pr.getMergedAt())
                .closedAt(pr.getClosedAt())
                .externalUpdatedAt(pr.getExternalUpdatedAt())
                .cycleTimeHours(pr.getCycleTimeHours())
                .timeToFirstReviewHours(pr.getTimeToFirstReviewHours())
                .timeToFirstApprovalHours(pr.getTimeToFirstApprovalHours())
                .linkedWorkItemExternalId(pr.getLinkedWorkItemExternalId())
                .draft(pr.getDraft())
                .baseBranch(pr.getBaseBranch())
                .sourceBranch(pr.getSourceBranch())
                .labels(pr.getLabels())
                .earliestCommitAt(pr.getEarliestCommitAt())
                .active(pr.isActive())
                .updatedAt(pr.getUpdatedAt())
                .version(pr.getVersion())
                .build();
    }

    public VcsCommit toDomain(VcsCommitDocument doc) {
        if (doc == null) return null;
        VcsCommit commit = new VcsCommit(doc.getId(), doc.getOrganizationId(), doc.getRepositoryId(),
                doc.getProvider(), doc.getSha());
        commit.setMessage(doc.getMessage());
        commit.setAuthorLogin(doc.getAuthorLogin());
        commit.setAuthorExternalId(doc.getAuthorExternalId());
        commit.setAuthorEmail(doc.getAuthorEmail());
        commit.setAuthorUserId(doc.getAuthorUserId());
        commit.setAuthoredAt(doc.getAuthoredAt());
        commit.setActive(doc.isActive());
        commit.setCreatedAt(doc.getCreatedAt());
        commit.setUpdatedAt(doc.getUpdatedAt());
        commit.setVersion(doc.getVersion());
        return commit;
    }

    public VcsCommitDocument toDocument(VcsCommit commit) {
        return VcsCommitDocument.builder()
                .id(commit.getId())
                .organizationId(commit.getOrganizationId())
                .repositoryId(commit.getRepositoryId())
                .provider(commit.getProvider())
                .sha(commit.getSha())
                .message(commit.getMessage())
                .authorLogin(commit.getAuthorLogin())
                .authorExternalId(commit.getAuthorExternalId())
                .authorEmail(commit.getAuthorEmail())
                .authorUserId(commit.getAuthorUserId())
                .authoredAt(commit.getAuthoredAt())
                .active(commit.isActive())
                .createdAt(commit.getCreatedAt())
                .updatedAt(commit.getUpdatedAt())
                .version(commit.getVersion())
                .build();
    }

    public VcsReviewEvent toDomain(VcsReviewEventDocument doc) {
        if (doc == null) return null;
        VcsReviewEvent event = new VcsReviewEvent(doc.getId(), doc.getOrganizationId(), doc.getPullRequestId(),
                doc.getRepositoryId(), doc.getProvider(), doc.getExternalEventId(), doc.getKind(), doc.getOccurredAt());
        event.setActorExternalId(doc.getActorExternalId());
        event.setActorLogin(doc.getActorLogin());
        event.setActorUserId(doc.getActorUserId());
        event.setBot(doc.isBot());
        event.setDeleted(doc.isDeleted());
        event.setActive(doc.isActive());
        event.setCreatedAt(doc.getCreatedAt());
        event.setUpdatedAt(doc.getUpdatedAt());
        event.setVersion(doc.getVersion());
        return event;
    }

    public VcsReviewEventDocument toDocument(VcsReviewEvent event) {
        return VcsReviewEventDocument.builder()
                .id(event.getId())
                .organizationId(event.getOrganizationId())
                .pullRequestId(event.getPullRequestId())
                .repositoryId(event.getRepositoryId())
                .provider(event.getProvider())
                .externalEventId(event.getExternalEventId())
                .actorExternalId(event.getActorExternalId())
                .actorLogin(event.getActorLogin())
                .actorUserId(event.getActorUserId())
                .bot(event.isBot())
                .kind(event.getKind())
                .occurredAt(event.getOccurredAt())
                .deleted(event.isDeleted())
                .active(event.isActive())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .version(event.getVersion())
                .build();
    }
}
