/*
 * Permission impact report — project-scoped-permissions task 1.1 (READ-ONLY).
 *
 * Lists, per organization, the active users who will LOSE access to project data when the change
 * is deployed: users whose role grants PROJECT_SCOPABLE codes (AZURE_READ, REPOS_READ,
 * DASHBOARD_TEAM, DASHBOARD_STRATEGIC, AI_READ_ALL, WORK_ITEM_MANAGE) but who will not receive
 * PROJECT_ACCESS_ALL, together with the active projects they are NOT a member of.
 *
 * Who keeps full access (skipped): roles holding USER_MANAGE (migration V__project_access_all) and
 * the default ADMIN / MANAGER presets (RoleSeeder). Everyone else keeps access only to the projects
 * whose `memberIds` contain them.
 *
 * APPROXIMATION: there is no access log, so the report cannot tell whether a user actually opened a
 * non-member project's dashboards/repositories before; it lists what they COULD read today and will
 * not read after the deploy. Share it with the admins so they fix memberships (or grant
 * PROJECT_ACCESS_ALL / project-level overrides) before or right after the deploy.
 *
 * The script only runs find() queries — it never writes. It is NOT wired to the application and is
 * never executed automatically.
 *
 * How to run (against a replica/secondary or a read-only user whenever possible):
 *
 *   mongosh "mongodb://<user>:<password>@<host>:<port>/andromeda?authSource=admin" \
 *       --quiet --file andromeda-ms/scripts/permission-impact-report.js
 *
 * Optional: restrict to one organization by defining ORG_ID before loading the file:
 *
 *   mongosh "<uri>" --quiet --eval 'var ORG_ID = "<organizationId>"' \
 *       --file andromeda-ms/scripts/permission-impact-report.js
 *
 * Output: one JSON line per affected user, then one summary line per organization.
 */

const PROJECT_SCOPABLE = ['AZURE_READ', 'REPOS_READ', 'DASHBOARD_TEAM', 'DASHBOARD_STRATEGIC', 'AI_READ_ALL',
  'WORK_ITEM_MANAGE'];
const LEGACY_TO_CURRENT = { GITHUB_READ: 'REPOS_READ', GITHUB_CONFIGURE: 'REPOS_CONFIGURE' };
const DEFAULT_PRESETS_WITH_ACCESS_ALL = ['ADMIN', 'MANAGER'];

const onlyOrg = (typeof ORG_ID !== 'undefined') ? ORG_ID : null;

function canonical(codes) {
  return [...new Set((codes || []).map((c) => LEGACY_TO_CURRENT[c] || c))];
}

function willHoldAccessAll(role, codes) {
  if (codes.includes('USER_MANAGE') || codes.includes('PROJECT_ACCESS_ALL')) return true;
  return role.organizationId == null && DEFAULT_PRESETS_WITH_ACCESS_ALL.includes(role.name);
}

const rolesById = {};
db.roles.find({ active: true }, { organizationId: 1, name: 1, permissionCodes: 1 })
  .forEach((role) => { rolesById[String(role._id)] = role; });

const orgFilter = { active: true };
if (onlyOrg) orgFilter._id = ObjectId.isValid(onlyOrg) ? ObjectId(onlyOrg) : onlyOrg;

db.organizations.find(orgFilter, { name: 1, slug: 1 }).forEach((org) => {
  const orgId = String(org._id);
  const projects = db.projects.find({ organizationId: orgId, active: true }, { name: 1, memberIds: 1 }).toArray();
  let affected = 0;

  db.users.find({ organizationId: orgId, active: true }, { email: 1, name: 1, roleId: 1 }).forEach((user) => {
    const userId = String(user._id);
    const role = rolesById[String(user.roleId)];
    if (!role) return;
    const codes = canonical(role.permissionCodes);
    if (willHoldAccessAll(role, codes)) return;
    const scopable = codes.filter((c) => PROJECT_SCOPABLE.includes(c));
    if (scopable.length === 0) return;

    const losing = projects.filter((p) => !(p.memberIds || []).includes(userId));
    if (losing.length === 0) return;
    affected += 1;
    print(JSON.stringify({
      organization: org.slug || org.name,
      organizationId: orgId,
      userId,
      email: user.email,
      name: user.name || null,
      role: role.name,
      projectScopableCodes: scopable,
      memberOf: projects.filter((p) => (p.memberIds || []).includes(userId)).map((p) => p.name),
      losesAccessTo: losing.map((p) => ({ projectId: String(p._id), name: p.name })),
    }));
  });

  print(JSON.stringify({ summary: true, organization: org.slug || org.name, organizationId: orgId,
    activeProjects: projects.length, affectedUsers: affected }));
});
