package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A comment of a pull request thread; {@code commentType} is {@code text}, {@code codeChange} or {@code system}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzCommentDto(Integer id, Integer parentCommentId, AzIdentityDto author, String content,
                           String publishedDate, String commentType,
                           @JsonProperty("isDeleted") Boolean deleted) {

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
