package andromeda.sei.ms;

import andromeda.sei.ms.shared.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class MsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsApplication.class, args);
    }
}
