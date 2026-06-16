package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class AzureClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AzureClientConfig.class);

    @Bean
    public WebClient azureWebClient() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .filter(loggingFilter())
                .build();
    }

    private ExchangeFilterFunction loggingFilter() {
        return (request, next) -> {
            ClientRequest sanitized = ClientRequest.from(request)
                    .headers(h -> h.remove("Authorization"))
                    .build();
            log.info("Azure API request: method={} url={}", sanitized.method(), sanitized.url());
            return next.exchange(request).doOnNext(response ->
                    log.info("Azure API response: method={} url={} status={}",
                            sanitized.method(), sanitized.url(), response.statusCode().value()));
        };
    }
}
