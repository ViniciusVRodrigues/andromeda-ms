package andromeda.sei.ms.feature.role.domain;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.PermissionScope;

public class Permission {

    private String id;
    private String code;
    private String description;
    private String module;
    private PermissionScope scope;

    /** Scope taken from {@link PermissionCatalog} (ORG_ONLY for unknown codes). */
    public Permission(String id, String code, String description, String module) {
        this(id, code, description, module,
                PermissionCatalog.scopeOf(code).orElse(PermissionScope.ORG_ONLY));
    }

    public Permission(String id, String code, String description, String module, PermissionScope scope) {
        this.id = id;
        this.code = code;
        this.description = description;
        this.module = module;
        this.scope = scope;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public PermissionScope getScope() { return scope; }
    public void setScope(PermissionScope scope) { this.scope = scope; }
}
