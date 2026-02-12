package ee.buerokratt.adauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AdAuthServiceApplication
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class AdAuthServiceApplicationTest {

    @Test
    void contextLoads() {
        // Given: Spring application context
        // When: Application starts
        // Then: Should load without exceptions
        assertTrue(true); // If we get here, context loaded successfully
    }

    @Test
    void applicationContextContainsRequiredBeans() {
        // This test will be enhanced with actual bean checks
        // For now, it ensures the context loads
        assertTrue(true);
    }
}
