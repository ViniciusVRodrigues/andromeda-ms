package andromeda.sei.ms.architecture;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.infrastructure.security.ProjectPermissionEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * project-scoped-permissions 6.5 — every endpoint that receives a {@code projectId},
 * {@code sprintId}, {@code repositoryId} or {@code bindingId} checks it through the {@code perm}
 * evaluator ({@code @PreAuthorize("@perm....")}), or is listed in {@link #ALLOWLIST} with the reason
 * it does not need a per-project check. A second check parses every {@code @PreAuthorize} and
 * asserts each {@code @perm.x(...)} call exists on {@link ProjectPermissionEvaluator} with that
 * arity — no other test evaluates those strings, so a typo would only fail at runtime.
 */
class ProjectScopedEndpointsArchitectureTest {

    private static final String BASE_PACKAGE = "andromeda.sei.ms";
    private static final Set<String> SCOPED_PARAMS = Set.of("projectId", "sprintId", "repositoryId", "bindingId");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)}");
    private static final Pattern AUTHORITY_CODES = Pattern.compile("'([A-Z_]+)'");
    /**
     * A path variable right after a {@code /projects/} segment is a project id whatever its name
     * ({@code /api/v1/projects/{id}}): without this the project mutations of {@code ProjectController}
     * were invisible to the scan, which only knew parameters literally named {@code projectId}.
     */
    private static final Pattern PROJECT_PATH_VARIABLE = Pattern.compile("/projects/\\{(\\w+)}");
    private static final String PROJECT_ID = "projectId";

    private static final String AZURE_CONFIG = "Azure DevOps configuration: AZURE_CONFIGURE is ORG_ONLY; the use case "
            + "resolves the project/binding inside the caller's organization (404 otherwise)";
    private static final String REPOS_CONFIG = "Repository binding configuration: REPOS_CONFIGURE is ORG_ONLY; the use "
            + "case resolves the project/binding inside the caller's organization (404 otherwise)";
    private static final String PROJECT_MANAGE = "Project metadata mutation: PROJECT_MANAGE is ORG_ONLY; the use case "
            + "resolves the project inside the caller's organization (404 otherwise)";
    private static final String PROJECT_MEMBERS = "Project membership: USER_MANAGE is ORG_ONLY; the use case resolves "
            + "the project inside the caller's organization (404 otherwise)";
    private static final String LEGACY = "308 redirect to the /api/v1/repos equivalent, which enforces @perm; "
            + "reads no data";

    /** {@code Controller#method} -> why a per-project {@code @perm} check is not required. */
    static final Map<String, String> ALLOWLIST = Map.ofEntries(
            entry("AzureProjectBindingController#bindProject", AZURE_CONFIG),
            entry("AzureProjectBindingController#removeBinding", AZURE_CONFIG),
            entry("AzureProjectBindingController#resyncProject", AZURE_CONFIG),
            entry("AzureProjectBindingController#repair", AZURE_CONFIG),
            entry("AzureProjectBindingController#wipeData", AZURE_CONFIG),
            entry("AzureProjectBindingController#updateSync", AZURE_CONFIG),
            entry("AzureProjectBindingController#getBinding", AZURE_CONFIG),
            entry("AzureProjectBindingController#updateWorkflowConfig", AZURE_CONFIG),
            entry("AzureProjectBindingController#listWorkItemTypes", AZURE_CONFIG),
            entry("AzureProjectBindingController#listWorkItemTypeFields", AZURE_CONFIG),
            entry("AzureProjectBindingController#listWorkItemTypeStates", AZURE_CONFIG),
            entry("AzureProjectBindingController#listSiblings", AZURE_CONFIG),
            entry("ProjectController#update", PROJECT_MANAGE),
            entry("ProjectController#deactivate", PROJECT_MANAGE),
            entry("ProjectController#updateMembers", PROJECT_MEMBERS),
            entry("GithubConnectionController#repair", REPOS_CONFIG),
            entry("GithubConnectionController#wipeData", REPOS_CONFIG),
            entry("VcsRepositoryBindingController#bind", REPOS_CONFIG),
            entry("VcsRepositoryBindingController#listProjectBindings", REPOS_CONFIG),
            entry("VcsRepositoryBindingController#update", REPOS_CONFIG),
            entry("VcsRepositoryBindingController#listBindingBranches", REPOS_CONFIG),
            entry("VcsRepositoryBindingController#unbind", REPOS_CONFIG),
            entry("LegacyGithubRoutesController#projectRepos", LEGACY),
            entry("LegacyGithubRoutesController#projectBindings", LEGACY),
            entry("LegacyGithubRoutesController#unbind", LEGACY),
            entry("LegacyGithubRoutesController#toggle", LEGACY)
    );

    private static List<Class<?>> controllers;

    @BeforeAll
    static void scan() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            controllers.add(Class.forName(definition.getBeanClassName()));
        }
    }

    @Test
    void should_compileWithParameterNames_soTheScanSeesThem() throws NoSuchMethodException {
        Method method = ProjectPermissionEvaluator.class.getMethod("onProject", String.class, String.class);
        assertThat(method.getParameters()[0].getName()).isEqualTo("projectId");
        assertThat(controllers).hasSizeGreaterThan(20);
    }

    @Test
    void should_checkEveryProjectScopedEndpoint_withPerm_orBeAllowlisted() {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : handlerMethods(controller)) {
                if (scopedParams(controller, method).isEmpty()) continue;
                String key = key(controller, method);
                String expression = preAuthorize(controller, method);
                if (expression != null && expression.contains("@perm.")) continue;
                if (!ALLOWLIST.containsKey(key)) violations.add(key + " " + scopedParams(controller, method));
            }
        }
        assertThat(violations).as("project-scoped endpoints without @perm nor allowlist entry").isEmpty();
    }

    @Test
    void should_treatIdAfterProjectsSegment_asProjectScoped() throws ClassNotFoundException {
        Class<?> controller = Class.forName(BASE_PACKAGE + ".feature.project.presentation.ProjectController");
        List<String> scoped = handlerMethods(controller).stream()
                .filter(m -> !scopedParams(controller, m).isEmpty())
                .map(Method::getName)
                .toList();

        assertThat(scoped).contains("update", "deactivate", "updateMembers", "listSprints")
                .doesNotContain("create", "list");
    }

    @Test
    void should_keepAllowlistMinimal_andLimitedToOrgOnlyCodes() {
        Set<String> seen = new HashSet<>();
        for (Class<?> controller : controllers) {
            for (Method method : handlerMethods(controller)) {
                String key = key(controller, method);
                if (!ALLOWLIST.containsKey(key)) continue;
                seen.add(key);
                String expression = preAuthorize(controller, method);
                assertThat(expression == null || !expression.contains("@perm."))
                        .as("%s already uses @perm: remove it from the allowlist", key).isTrue();
                assertThat(scopedParams(controller, method)).as("%s has no scoped parameter", key).isNotEmpty();
                if (expression == null) continue; // legacy redirects carry no data
                Matcher codes = AUTHORITY_CODES.matcher(expression);
                while (codes.find()) {
                    assertThat(PermissionCatalog.isProjectScopable(codes.group(1)))
                            .as("%s is allowlisted but requires the PROJECT_SCOPABLE %s", key, codes.group(1))
                            .isFalse();
                }
            }
        }
        assertThat(seen).as("stale allowlist entries").containsExactlyInAnyOrderElementsOf(ALLOWLIST.keySet());
    }

    @Test
    void should_referenceOnlyExistingPermMethods_withMatchingArity() {
        SpelExpressionParser parser = new SpelExpressionParser();
        List<String> problems = new ArrayList<>();
        int calls = 0;
        for (Class<?> controller : controllers) {
            for (Method method : handlerMethods(controller)) {
                String expression = preAuthorize(controller, method);
                if (expression == null || !expression.contains("@perm.")) continue;
                List<SpelNode[]> permCalls = new ArrayList<>();
                collectPermCalls(((SpelExpression) parser.parseExpression(expression)).getAST(), permCalls);
                for (SpelNode[] call : permCalls) {
                    calls++;
                    MethodReference reference = (MethodReference) call[1];
                    if (!evaluatorHas(reference.getName(), reference.getChildCount())) {
                        problems.add(key(controller, method) + " -> @perm." + reference.getName()
                                + "/" + reference.getChildCount());
                    }
                }
            }
        }
        assertThat(problems).as("unknown @perm methods").isEmpty();
        assertThat(calls).isGreaterThan(20);
    }

    private static void collectPermCalls(SpelNode node, List<SpelNode[]> out) {
        for (int i = 0; i + 1 < node.getChildCount(); i++) {
            SpelNode current = node.getChild(i);
            SpelNode next = node.getChild(i + 1);
            if (current instanceof BeanReference && "@perm".equals(current.toStringAST())
                    && next instanceof MethodReference) {
                out.add(new SpelNode[]{current, next});
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) collectPermCalls(node.getChild(i), out);
    }

    private static boolean evaluatorHas(String name, int arity) {
        return Arrays.stream(ProjectPermissionEvaluator.class.getMethods())
                .anyMatch(m -> m.getName().equals(name) && m.getParameterCount() == arity
                        && m.getReturnType() == boolean.class);
    }

    private static List<Method> handlerMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
                .toList();
    }

    private static Set<String> scopedParams(Class<?> controller, Method method) {
        Set<String> names = new HashSet<>();
        for (Parameter parameter : method.getParameters()) {
            PathVariable path = parameter.getAnnotation(PathVariable.class);
            RequestParam query = parameter.getAnnotation(RequestParam.class);
            if (path == null && query == null) continue;
            String explicit = path != null ? first(path.name(), path.value()) : first(query.name(), query.value());
            names.add(explicit.isEmpty() ? parameter.getName() : explicit);
        }
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (classMapping != null) {
            for (String path : classMapping.path()) {
                Matcher variables = PATH_VARIABLE.matcher(path);
                while (variables.find()) names.add(variables.group(1));
            }
        }
        Set<String> projectVariables = projectPathVariables(classMapping, method);
        if (names.removeAll(projectVariables)) names.add(PROJECT_ID);
        names.retainAll(SCOPED_PARAMS);
        return names;
    }

    /** Names of the path variables that follow a {@code /projects/} segment in the full mapping. */
    private static Set<String> projectPathVariables(RequestMapping classMapping, Method method) {
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        List<String> prefixes = classMapping == null || classMapping.path().length == 0
                ? List.of("") : List.of(classMapping.path());
        List<String> suffixes = methodMapping == null || methodMapping.path().length == 0
                ? List.of("") : List.of(methodMapping.path());
        Set<String> variables = new HashSet<>();
        for (String prefix : prefixes) {
            for (String suffix : suffixes) {
                Matcher matcher = PROJECT_PATH_VARIABLE.matcher(prefix + (suffix.startsWith("/") ? "" : "/") + suffix);
                while (matcher.find()) variables.add(matcher.group(1));
            }
        }
        return variables;
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static String preAuthorize(Class<?> controller, Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        return annotation == null ? null : annotation.value();
    }

    private static String key(Class<?> controller, Method method) {
        return controller.getSimpleName() + "#" + method.getName();
    }
}
