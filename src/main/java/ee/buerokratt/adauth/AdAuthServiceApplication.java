package ee.buerokratt.adauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Application Class for AD-Auth Service
 *
 * This service provides SAML 2.0 based Active Directory authentication
 * for the Bürokratt ecosystem.
 *
 * @author Bürokratt Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class AdAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdAuthServiceApplication.class, args);
    }
}
