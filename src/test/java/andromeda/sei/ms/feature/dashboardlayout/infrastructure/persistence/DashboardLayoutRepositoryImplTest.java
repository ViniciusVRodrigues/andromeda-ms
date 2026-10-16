package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardWidget;
import andromeda.sei.ms.feature.dashboardlayout.domain.WidgetSize;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutVersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Persistence of {@code dashboard_layouts} without a database (fast, query-shape level). Proves that
 * <ul>
 *     <li>every derived query filters by organizationId + userId + active = true (parsed with the
 *     same {@link PartTree} Spring Data uses to build the Mongo query);</li>
 *     <li>the adapter passes the tenant and user through and never deletes (soft delete = save);</li>
 *     <li>the document declares the partial unique index and the org/active index.</li>
 * </ul>
 * The partial-index semantics against a real Mongo are in {@code DashboardLayoutPartialIndexMongoTest}.
 */
@ExtendWith(MockitoExtension.class)
class DashboardLayoutRepositoryImplTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private DashboardLayoutMongoRepository mongoRepository;

    private DashboardLayoutRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new DashboardLayoutRepositoryImpl(mongoRepository, new DashboardLayoutDocumentMapper());
    }

    @Test
    void should_filterSingleLookup_byOrgUserViewAndActive() {
        assertThat(criteria("findByOrganizationIdAndUserIdAndViewKeyAndActiveTrue")).containsExactlyInAnyOrderEntriesOf(
                Map.of("organizationId", Part.Type.SIMPLE_PROPERTY, "userId", Part.Type.SIMPLE_PROPERTY,
                        "viewKey", Part.Type.SIMPLE_PROPERTY, "active", Part.Type.TRUE));
    }

    @Test
    void should_filterListing_byOrgUserAndActive() {
        assertThat(criteria("findAllByOrganizationIdAndUserIdAndActiveTrue")).containsExactlyInAnyOrderEntriesOf(
                Map.of("organizationId", Part.Type.SIMPLE_PROPERTY, "userId", Part.Type.SIMPLE_PROPERTY,
                        "active", Part.Type.TRUE));
    }

    @Test
    void should_declareOnlyTenantScopedFinders_onMongoRepository() {
        List<String> declared = Arrays.stream(DashboardLayoutMongoRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();

        assertThat(declared).allSatisfy(name -> assertThat(name)
                .contains("OrganizationId").contains("UserId").endsWith("ActiveTrue"));
    }

    @Test
    void should_passTenantUserAndViewValue_toLookup() {
        when(mongoRepository.findByOrganizationIdAndUserIdAndViewKeyAndActiveTrue(ORG, USER, "equipe"))
                .thenReturn(Optional.of(document("layout-1", true, 3L)));

        Optional<DashboardLayout> found = repository.findActive(ORG, USER, DashboardViewKey.EQUIPE);

        assertThat(found).isPresent();
        assertThat(found.get().getViewKey()).isEqualTo(DashboardViewKey.EQUIPE);
        assertThat(found.get().getSections().getFirst().widgets().getFirst())
                .isEqualTo(new DashboardWidget("equipe.burndown", WidgetSize.G));
        assertThat(found.get().getHidden()).containsExactly("equipe.commits-dia");
    }

    @Test
    void should_passTenantAndUser_toListing() {
        when(mongoRepository.findAllByOrganizationIdAndUserIdAndActiveTrue(ORG, USER))
                .thenReturn(List.of(document("layout-1", true, 1L)));

        assertThat(repository.findAllActive(ORG, USER)).hasSize(1);
        verify(mongoRepository).findAllByOrganizationIdAndUserIdAndActiveTrue(ORG, USER);
    }

    @Test
    void should_stampTimestamps_andKeepTenant_onCreate() {
        when(mongoRepository.save(any())).thenAnswer(inv -> withVersion(inv.getArgument(0), 0L));

        DashboardLayout saved = repository.save(DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE,
                List.of(new DashboardSection("fluxo", true, List.of(new DashboardWidget("equipe.burndown", WidgetSize.G)))),
                List.of()));

        DashboardLayoutDocument doc = captureSaved();
        assertThat(doc.getId()).isNull();
        assertThat(doc.getOrganizationId()).isEqualTo(ORG);
        assertThat(doc.getUserId()).isEqualTo(USER);
        assertThat(doc.getViewKey()).isEqualTo("equipe");
        assertThat(doc.isActive()).isTrue();
        assertThat(doc.getVersion()).isNull();
        assertThat(doc.getCreatedAt()).isNotNull().isEqualTo(doc.getUpdatedAt());
        assertThat(doc.getSections().getFirst().isCollapsed()).isTrue();
        assertThat(doc.getSections().getFirst().getWidgets().getFirst().getSize()).isEqualTo("G");
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void should_softDelete_bySavingInactive_neverDeleting() {
        DashboardLayout layout = new DashboardLayout("layout-1", ORG, USER, DashboardViewKey.EQUIPE,
                List.of(), List.of(), true, 3L, CREATED, CREATED);
        layout.deactivate();
        when(mongoRepository.save(any())).thenAnswer(inv -> withVersion(inv.getArgument(0), 4L));

        repository.save(layout);

        DashboardLayoutDocument doc = captureSaved();
        assertThat(doc.getId()).isEqualTo("layout-1");
        assertThat(doc.isActive()).isFalse();
        assertThat(doc.getVersion()).isEqualTo(3L);
        assertThat(doc.getCreatedAt()).isEqualTo(CREATED);
        assertThat(doc.getUpdatedAt()).isAfter(CREATED);
        verifyNoMoreInteractions(mongoRepository);
    }

    @Test
    void should_translateStaleVersion_toConflict() {
        when(mongoRepository.save(any())).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> repository.save(existing()))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
    }

    @Test
    void should_translateSecondActiveLayout_toConflict() {
        when(mongoRepository.save(any())).thenThrow(new DuplicateKeyException("uk_org_user_view_active"));

        assertThatThrownBy(() -> repository.save(existing()))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
    }

    @Test
    void should_declarePartialUniqueIndex_onOrgUserView() {
        Map<String, CompoundIndex> indexes = indexes();

        CompoundIndex unique = indexes.get(DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX);
        assertThat(unique).isNotNull();
        assertThat(unique.def()).isEqualTo("{'organizationId': 1, 'userId': 1, 'viewKey': 1}");
        assertThat(unique.unique()).isTrue();
        assertThat(unique.partialFilter()).isEqualTo("{'active': true}");
    }

    @Test
    void should_declareOrgActiveIndex() {
        CompoundIndex orgActive = indexes().get(DashboardLayoutDocument.ORG_ACTIVE_INDEX);

        assertThat(orgActive).isNotNull();
        assertThat(orgActive.def()).isEqualTo("{'organizationId': 1, 'active': 1}");
        assertThat(orgActive.unique()).isFalse();
    }

    @Test
    void should_mapToCollection_withVersionField() throws NoSuchFieldException {
        assertThat(DashboardLayoutDocument.class
                .getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class).collection())
                .isEqualTo("dashboard_layouts");
        assertThat(DashboardLayoutDocument.class.getDeclaredField("version")
                .isAnnotationPresent(org.springframework.data.annotation.Version.class)).isTrue();
    }

    private static Map<String, Part.Type> criteria(String methodName) {
        return new PartTree(methodName, DashboardLayoutDocument.class).getParts().stream()
                .collect(Collectors.toMap(part -> part.getProperty().toDotPath(), Part::getType));
    }

    private static Map<String, CompoundIndex> indexes() {
        return Arrays.stream(DashboardLayoutDocument.class
                        .getAnnotation(org.springframework.data.mongodb.core.index.CompoundIndexes.class).value())
                .collect(Collectors.toMap(CompoundIndex::name, index -> index));
    }

    private DashboardLayoutDocument captureSaved() {
        ArgumentCaptor<DashboardLayoutDocument> captor = ArgumentCaptor.forClass(DashboardLayoutDocument.class);
        verify(mongoRepository).save(captor.capture());
        return captor.getValue();
    }

    private static DashboardLayout existing() {
        return new DashboardLayout("layout-1", ORG, USER, DashboardViewKey.EQUIPE, List.of(), List.of(), true, 3L,
                CREATED, CREATED);
    }

    private static DashboardLayoutDocument withVersion(DashboardLayoutDocument doc, long version) {
        return DashboardLayoutDocument.builder()
                .id(doc.getId() == null ? "layout-1" : doc.getId())
                .organizationId(doc.getOrganizationId()).userId(doc.getUserId()).viewKey(doc.getViewKey())
                .sections(doc.getSections()).hidden(doc.getHidden()).active(doc.isActive()).version(version)
                .createdAt(doc.getCreatedAt()).updatedAt(doc.getUpdatedAt())
                .build();
    }

    private static DashboardLayoutDocument document(String id, boolean active, long version) {
        return DashboardLayoutDocument.builder()
                .id(id).organizationId(ORG).userId(USER).viewKey("equipe")
                .sections(List.of(new DashboardLayoutDocument.SectionDocument("fluxo", false,
                        List.of(new DashboardLayoutDocument.WidgetDocument("equipe.burndown", "G")))))
                .hidden(List.of("equipe.commits-dia"))
                .active(active).version(version).createdAt(CREATED).updatedAt(CREATED)
                .build();
    }
}
