package andromeda.sei.ms.feature.allocation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import andromeda.sei.ms.feature.allocation.presentation.dto.SprintItemResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SprintItemResponseContractTest {

    @Test
    void should_serializeComplexityAndEffort_withoutStoryPoints() throws Exception {
        JsonNode json = new ObjectMapper().valueToTree(
                new SprintItemResponse("wi-1", "T", "Sprint 1", "STORY", "10", 3.0, 12.0));

        assertThat(json.has("storyPoints")).isFalse();
        assertThat(json.get("complexity").asDouble()).isEqualTo(3.0);
        assertThat(json.get("effort").asDouble()).isEqualTo(12.0);
    }
}
