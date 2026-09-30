package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Azure DevOps {@code GitPullRequestCommentThread}. {@code properties} is the typed property bag
 * ({@code {"$type": "System.String", "$value": "VoteUpdate"}}) that carries the system thread
 * kind and the vote.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzThreadDto(Integer id, String publishedDate, String lastUpdatedDate, List<AzCommentDto> comments,
                          Map<String, Property> properties, @JsonProperty("isDeleted") Boolean deleted) {

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    /** The value of a property as a string, or {@code null} when absent. */
    public String property(String name) {
        if (properties == null) return null;
        Property property = properties.get(name);
        return property == null || property.value() == null ? null : String.valueOf(property.value());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Property(@JsonProperty("$type") String type, @JsonProperty("$value") Object value) {}
}
