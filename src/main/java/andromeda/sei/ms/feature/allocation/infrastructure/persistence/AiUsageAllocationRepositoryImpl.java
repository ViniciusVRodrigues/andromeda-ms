package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AiUsageAllocationRepositoryImpl implements AiUsageAllocationRepository {

    private final AiUsageAllocationMongoRepository mongoRepository;
    private final AiUsageAllocationDocumentMapper mapper;

    @Override
    public List<AiUsageAllocation> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findPendingByOrganizationIdAndUserId(
            String organizationId, String userId) {
        return mongoRepository.findByOrganizationIdAndUserIdAndStatusIn(
                        organizationId, userId,
                        List.of(AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.Status.UNALLOCATED))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsValidatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.existsByOrganizationIdAndUserIdAndDayAndStatus(
                organizationId, userId, day, AiUsageAllocation.Status.VALIDATED);
    }

    @Override
    public void saveAll(List<AiUsageAllocation> allocations) {
        mongoRepository.saveAll(allocations.stream().map(mapper::toDocument).toList());
    }

    @Override
    public void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        mongoRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);
    }
}
