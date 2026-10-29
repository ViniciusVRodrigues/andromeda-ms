package andromeda.sei.ms.feature.agent.domain;

/**
 * Authorities carried by an authenticated local agent. They are NOT permission codes of the
 * catalog (never in the PermissionSeeder, never in a role): the agent token never inherits the
 * permissions of the person who paired it.
 */
public final class AgentAuthorities {

    public static final String AGENT_USAGE_WRITE = "AGENT_USAGE_WRITE";
    public static final String AGENT_TASKS_READ = "AGENT_TASKS_READ";

    /** OAuth scope string returned with the token pair. */
    public static final String SCOPE = "usage:write tasks:read";

    private AgentAuthorities() {
    }
}
