package andromeda.sei.ms.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Resolves the indexes declared on the {@code @Document} classes ({@code @CompoundIndex},
 * {@code @Indexed}, ...) with Spring Data's own {@link MongoPersistentEntityIndexResolver}, so names,
 * keys and options are exactly what Spring Data's auto index creation would produce.
 *
 * <p>The document classes are found by scanning the classpath, not by reading the mapping context:
 * the context only holds the entities a repository or template has touched so far
 * ({@code AbstractMongoClientConfiguration} scans just {@code MongoConfig}'s own package).
 */
@Slf4j
final class DeclaredIndexCatalog {

    private static final String NAME = "name";
    private static final String KEY_SEPARATOR = "_";

    private DeclaredIndexCatalog() {
    }

    /** One declared index and the collection it belongs to. */
    record DeclaredIndex(String collection, IndexDefinition definition) {

        DeclaredIndex {
            Objects.requireNonNull(collection, "collection");
            Objects.requireNonNull(definition, "definition");
        }

        String name() {
            return effectiveName(definition);
        }
    }

    static List<DeclaredIndex> resolve(MongoMappingContext mappingContext, String basePackage) {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        List<DeclaredIndex> indexes = new ArrayList<>();
        for (Class<?> type : documentTypes(basePackage)) {
            indexes.addAll(resolveFor(resolver, mappingContext, type));
        }
        return indexes;
    }

    /** The name MongoDB will give the index: the explicit one, or its default {@code key_dir_key_dir}. */
    static String effectiveName(IndexDefinition definition) {
        Object explicit = definition.getIndexOptions().get(NAME);
        if (explicit instanceof String name && !name.isBlank()) return name;
        return definition.getIndexKeys().entrySet().stream()
                .map(key -> key.getKey() + KEY_SEPARATOR + key.getValue())
                .collect(Collectors.joining(KEY_SEPARATOR));
    }

    private static List<DeclaredIndex> resolveFor(MongoPersistentEntityIndexResolver resolver,
                                                  MongoMappingContext mappingContext, Class<?> type) {
        List<DeclaredIndex> indexes = new ArrayList<>();
        try {
            String collection = mappingContext.getRequiredPersistentEntity(type).getCollection();
            for (IndexDefinition definition : resolver.resolveIndexFor(type)) {
                indexes.add(new DeclaredIndex(collection, definition));
            }
        } catch (RuntimeException e) {
            log.warn("DeclaredIndexInitializer: could not resolve the indexes declared on {} (startup continues): {}",
                    type.getName(), e.getMessage());
        }
        return indexes;
    }

    private static List<Class<?>> documentTypes(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.data.mongodb.core.mapping.Document.class));
        ClassLoader classLoader = DeclaredIndexCatalog.class.getClassLoader();
        return scanner.findCandidateComponents(basePackage).stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, classLoader))
                .toList();
    }
}
