/*
 * Complexity migration report — complexidade-esforco task 1.2 (READ-ONLY).
 *
 * Lists the Azure project bindings that ComplexityFromStoryPointsMigration will touch and how many
 * work items change in each one:
 *   - target: binding DEFAULT (no workflowConfig or configSource != CUSTOM), or CUSTOM with an
 *     EFFORT mapping to Microsoft.VSTS.Scheduling.StoryPoints and no COMPLEXITY mapping;
 *   - items that change: active, resolvedType STORY/FEATURE/BUG, complexity null,
 *     complexitySource and effortSource != MANUAL_OVERRIDE, effort not null; in a CUSTOM binding also
 *     only the raw types whose EFFORT field (exact type, then wildcard) is StoryPoints.
 * A CUSTOM binding listed here maps StoryPoints as Effort on purpose or by default: confirm it with
 * the product owner before the deploy.
 *
 * The script only runs find()/countDocuments() — it never writes.
 *
 * How to run (against a read-only user whenever possible):
 *
 *   mongosh "mongodb://<user>:<password>@<host>:<port>/andromeda?authSource=admin" \
 *       --quiet --file andromeda-ms/scripts/complexity-migration-report.js
 */
const STORY_POINTS = "Microsoft.VSTS.Scheduling.StoryPoints";

const STORY_TIER = ["STORY", "FEATURE", "BUG"];

// CUSTOM: only raw types whose EFFORT field resolves to StoryPoints (exact type wins over wildcard).
function rawTypeFilter(b, custom, mappings) {
    if (!custom) return {};
    const efforts = mappings.filter(m => m.internalField === "EFFORT");
    const wildcard = efforts.find(m => !m.workItemType);
    const exactTypes = [...new Set(efforts.filter(m => m.workItemType).map(m => m.workItemType))];
    const spExact = exactTypes.filter(t => efforts.some(m => m.workItemType === t && m.azureFieldReferenceName === STORY_POINTS));
    const otherExact = exactTypes.filter(t => !spExact.includes(t));
    const wildcardSp = wildcard && wildcard.azureFieldReferenceName === STORY_POINTS;
    return wildcardSp ? { rawType: { $nin: otherExact } } : { rawType: { $in: spExact } };
}

let bindings = 0;
let items = 0;
db.azure_project_bindings.find({ active: true }).forEach(b => {
    const cfg = b.workflowConfig || {};
    const mappings = cfg.fieldMappings || [];
    const custom = cfg.configSource === "CUSTOM";
    const effortOnSp = mappings.some(m => m.internalField === "EFFORT" && m.azureFieldReferenceName === STORY_POINTS);
    const hasComplexity = mappings.some(m => m.internalField === "COMPLEXITY");
    if (custom && !(effortOnSp && !hasComplexity)) return;

    const changing = db.work_items.countDocuments({
        organizationId: b.organizationId,
        projectId: b.projectId,
        active: true,
        resolvedType: { $in: STORY_TIER },
        complexity: null,
        complexitySource: { $ne: "MANUAL_OVERRIDE" },
        effortSource: { $ne: "MANUAL_OVERRIDE" },
        effort: { $ne: null },
        ...rawTypeFilter(b, custom, mappings),
    });
    const manualEffort = db.work_items.countDocuments({
        organizationId: b.organizationId,
        projectId: b.projectId,
        active: true,
        resolvedType: { $in: STORY_TIER },
        effortSource: "MANUAL_OVERRIDE",
    });
    bindings++;
    items += changing;
    print(`${custom ? "CUSTOM " : "DEFAULT"} org=${b.organizationId} project=${b.projectId} azure="${b.azureProjectName}" ` +
          `itemsChanging=${changing} storyItemsWithManualEffort=${manualEffort}`);
});
print(`TOTAL bindings=${bindings} itemsChanging=${items}`);
