package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutVersionConflictException;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Semantica do indice unico parcial de {@code dashboard_layouts} contra Mongo real: uma ativa por
 * (org, usuario, view); soft delete libera a chave; chaves diferentes convivem. O indice e criado
 * pelo {@code DeclaredIndexInitializer} (importado pela config de teste), como no startup.
 */
@MongoIntegrationTest
class DashboardLayoutPartialIndexMongoTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    @Autowired private DashboardLayoutMongoRepository mongoRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private DashboardLayoutRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new DashboardLayoutRepositoryImpl(mongoRepository, new DashboardLayoutDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void should_createPartialUniqueIndex_atStartup() {
        IndexInfo index = mongoTemplate.indexOps("dashboard_layouts").getIndexInfo().stream()
                .filter(i -> DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX.equals(i.getName()))
                .findFirst().orElseThrow();

        assertThat(index.isUnique()).isTrue();
        assertThat(index.getPartialFilterExpression()).contains("active");
    }

    @Test
    void should_saveAndFindActiveLayout() {
        repository.save(layout(DashboardViewKey.EQUIPE));

        assertThat(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).isPresent();
    }

    @Test
    void should_allowRecreating_afterSoftDelete_withSameKey() {
        DashboardLayout first = repository.save(layout(DashboardViewKey.EQUIPE));
        first.deactivate();
        repository.save(first);

        repository.save(layout(DashboardViewKey.EQUIPE));

        assertThat(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).isPresent();
        assertThat(mongoRepository.count()).isEqualTo(2);
    }

    @Test
    void should_allowManyInactive_withSameKey() {
        for (int i = 0; i < 3; i++) {
            DashboardLayout saved = repository.save(layout(DashboardViewKey.EQUIPE));
            saved.deactivate();
            repository.save(saved);
        }

        assertThat(mongoRepository.count()).isEqualTo(3);
        assertThat(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).isEmpty();
    }

    @Test
    void should_rejectSecondActiveLayout_withSameKey() {
        repository.save(layout(DashboardViewKey.EQUIPE));

        assertThatThrownBy(() -> repository.save(layout(DashboardViewKey.EQUIPE)))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
    }

    @Test
    void should_allowActiveLayouts_forDifferentViewUserOrOrg() {
        repository.save(layout(DashboardViewKey.EQUIPE));
        repository.save(layout(DashboardViewKey.INDIVIDUAL));
        repository.save(DashboardLayout.create(ORG, "user-2", DashboardViewKey.EQUIPE, List.of(), List.of()));
        repository.save(DashboardLayout.create("org-2", USER, DashboardViewKey.EQUIPE, List.of(), List.of()));

        assertThat(mongoRepository.count()).isEqualTo(4);
    }

    private static DashboardLayout layout(DashboardViewKey view) {
        return DashboardLayout.create(ORG, USER, view, List.of(), List.of());
    }
}
