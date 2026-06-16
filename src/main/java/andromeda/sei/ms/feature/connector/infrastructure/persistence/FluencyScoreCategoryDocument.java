package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FluencyScoreCategoryDocument {

    private String category;
    private String icon;
    private int stage;
    private List<String> tips;
}
