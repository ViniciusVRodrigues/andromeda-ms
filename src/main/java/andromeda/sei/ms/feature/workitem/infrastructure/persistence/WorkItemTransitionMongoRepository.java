package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorkItemTransitionMongoRepository extends MongoRepository<WorkItemTransitionDocument, String> {

    List<WorkItemTransitionDocument> findAllByWorkItemId(String workItemId);

    boolean existsByWorkItemId(String workItemId);
}
