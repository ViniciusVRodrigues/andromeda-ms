package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * One {@link VcsIntegrationDataWiper} per provider: GitHub under the GitHub integration, Azure
 * Repos under Azure DevOps next to the Boards wiper (azure-repos-provider task 6.3).
 */
@Configuration
public class VcsDataWiperConfig {

    @Bean
    public VcsIntegrationDataWiper githubVcsDataWiper(MongoTemplate mongoTemplate) {
        return new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.GITHUB);
    }

    @Bean
    public VcsIntegrationDataWiper azureReposVcsDataWiper(MongoTemplate mongoTemplate) {
        return new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.AZURE_REPOS);
    }
}
