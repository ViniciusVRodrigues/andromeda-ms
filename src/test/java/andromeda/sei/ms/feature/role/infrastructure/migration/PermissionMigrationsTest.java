package andromeda.sei.ms.feature.role.infrastructure.migration;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 2.4 — {@code V__permission_scopes}, {@code V__project_access_all} and
 * {@code V__project_manage}.
 * Against a mocked {@link MongoTemplate} (the repo's persistence tests are disabled under Boot 4):
 * the assertions are on the filters and update operators, which is where idempotency lives.
 */
@ExtendWith(MockitoExtension.class)
class PermissionMigrationsTest {

    @Mock private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(String.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    @Test
    void should_addProjectAccessAll_toEveryRoleHoldingUserManage_idempotently() {
        new ProjectAccessAllMigration(mongoTemplate).run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq("roles"));

        Document filter = query.getValue().getQueryObject();
        assertThat(filter).doesNotContainKey("organizationId"); // default and custom roles of every org
        Document codes = filter.get("permissionCodes", Document.class);
        assertThat(codes.get("$all", List.class)).containsExactly("USER_MANAGE");
        assertThat(codes.get("$nin", List.class)).containsExactly("PROJECT_ACCESS_ALL");
        Document operations = update.getValue().getUpdateObject();
        assertThat(operations.get("$addToSet", Document.class)).containsEntry("permissionCodes", "PROJECT_ACCESS_ALL");
        assertThat(operations.get("$inc", Document.class)).containsEntry("version", 1);
    }

    @Test
    void should_addProjectManage_toEveryRoleHoldingUserManage_idempotently() {
        new ProjectManageMigration(mongoTemplate).run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq("roles"));

        Document filter = query.getValue().getQueryObject();
        assertThat(filter).doesNotContainKey("organizationId"); // default and custom roles of every org
        Document codes = filter.get("permissionCodes", Document.class);
        assertThat(codes.get("$all", List.class)).containsExactly("USER_MANAGE");
        // Re-run safety: a role that already holds PROJECT_MANAGE no longer matches the filter.
        assertThat(codes.get("$nin", List.class)).containsExactly("PROJECT_MANAGE");
        Document operations = update.getValue().getUpdateObject();
        assertThat(operations.get("$addToSet", Document.class)).containsEntry("permissionCodes", "PROJECT_MANAGE");
        assertThat(operations.get("$inc", Document.class)).containsEntry("version", 1);
    }

    @Test
    void should_addOrganizationDelete_onlyToGlobalAdmin_idempotently() {
        new OrganizationDeleteMigration(mongoTemplate).run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq("roles"));

        Document filter = query.getValue().getQueryObject();
        assertThat(filter).containsEntry("organizationId", null); // only the global ADMIN, never an organization role
        assertThat(filter).containsEntry("name", "ADMIN");
        Document codes = filter.get("permissionCodes", Document.class);
        assertThat(codes.get("$nin", List.class)).containsExactly("ORGANIZATION_DELETE");
        Document operations = update.getValue().getUpdateObject();
        assertThat(operations.get("$addToSet", Document.class)).containsEntry("permissionCodes", "ORGANIZATION_DELETE");
        assertThat(operations.get("$inc", Document.class)).containsEntry("version", 1);
    }

    @Test
    void should_setScopeOfEveryCatalogCode_onlyWhereItDiffers() {
        new PermissionScopesMigration(mongoTemplate).run(null);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(PermissionCatalog.codes().size()))
                .updateMulti(queries.capture(), updates.capture(), eq("permissions"));

        for (int i = 0; i < queries.getAllValues().size(); i++) {
            Document filter = queries.getAllValues().get(i).getQueryObject();
            String code = filter.getString("code");
            String scope = PermissionCatalog.scopeOf(code).orElseThrow().name();
            assertThat(filter.get("scope", Document.class)).containsEntry("$ne", scope);
            assertThat(updates.getAllValues().get(i).getUpdateObject().get("$set", Document.class))
                    .containsEntry("scope", scope);
        }
        assertThat(queries.getAllValues()).extracting(q -> q.getQueryObject().getString("code"))
                .contains("DASHBOARD_TEAM", "PROJECT_ACCESS_ALL", "WORK_ITEM_MANAGE");
    }
}
