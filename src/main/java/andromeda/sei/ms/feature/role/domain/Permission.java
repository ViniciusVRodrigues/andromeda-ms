package andromeda.sei.ms.feature.role.domain;

public class Permission {

    private String id;
    private String code;
    private String description;
    private String module;

    public Permission(String id, String code, String description, String module) {
        this.id = id;
        this.code = code;
        this.description = description;
        this.module = module;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
}
