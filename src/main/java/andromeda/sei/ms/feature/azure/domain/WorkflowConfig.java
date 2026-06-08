package andromeda.sei.ms.feature.azure.domain;

import java.util.List;

public class WorkflowConfig {

    public enum ConfigSource { DEFAULT, CUSTOM }

    private List<StatusMapping> statusMappings;
    private List<TypeMapping> typeMappings;
    private ConfigSource configSource;

    public WorkflowConfig(List<StatusMapping> statusMappings, List<TypeMapping> typeMappings, ConfigSource configSource) {
        this.statusMappings = statusMappings;
        this.typeMappings = typeMappings;
        this.configSource = configSource;
    }

    public List<StatusMapping> getStatusMappings() { return statusMappings; }
    public void setStatusMappings(List<StatusMapping> statusMappings) { this.statusMappings = statusMappings; }

    public List<TypeMapping> getTypeMappings() { return typeMappings; }
    public void setTypeMappings(List<TypeMapping> typeMappings) { this.typeMappings = typeMappings; }

    public ConfigSource getConfigSource() { return configSource; }
    public void setConfigSource(ConfigSource configSource) { this.configSource = configSource; }
}
