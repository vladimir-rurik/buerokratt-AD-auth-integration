package ee.buerokratt.adauth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ADProperties
 */
class ADPropertiesTest {

    private ADProperties adProperties;

    @BeforeEach
    void setUp() {
        adProperties = new ADProperties();
    }

    @Test
    void testDefaultValues() {
        // Given: New ADProperties instance with nested objects initialized
        adProperties.setFederation(new ADProperties.Federation());
        adProperties.setRoleMapping(new ADProperties.RoleMapping());
        adProperties.setResilience(new ADProperties.Resilience());

        // When: Getting default values
        // Then: Should have sensible defaults
        assertNotNull(adProperties.getFederation());
        assertNotNull(adProperties.getRoleMapping());
        assertNotNull(adProperties.getResilience());
    }

    @Test
    void testFederationDefaults() {
        // Given: Federation config
        adProperties.setFederation(new ADProperties.Federation());

        // When: Getting defaults
        // Then: Should have correct defaults
        assertEquals(5000, adProperties.getFederation().getTimeout());
    }

    @Test
    void testRoleMappingDefaults() {
        // Given: RoleMapping config
        adProperties.setRoleMapping(new ADProperties.RoleMapping());

        // When: Getting defaults
        // Then: Should have correct defaults
        assertEquals("ROLE_UNAUTHENTICATED", adProperties.getRoleMapping().getDefaultRole());
        assertEquals(ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY,
                     adProperties.getRoleMapping().getMultiGroupStrategy());
    }

    @Test
    void testResilienceDefaults() {
        // Given: Resilience config
        adProperties.setResilience(new ADProperties.Resilience());

        // When: Getting defaults
        // Then: Should have correct defaults
        assertEquals(3, adProperties.getResilience().getMaxRetries());
        assertEquals(1000L, adProperties.getResilience().getRetryBackoff());
        assertEquals(50, adProperties.getResilience().getCircuitBreakerFailureThreshold());
        assertEquals(30000L, adProperties.getResilience().getCircuitBreakerWaitDuration());
    }

    @Test
    void testMultiGroupStrategies() {
        // When: Checking enum values
        // Then: Should have all strategies
        ADProperties.RoleMapping.MultiGroupStrategy[] strategies =
            ADProperties.RoleMapping.MultiGroupStrategy.values();

        assertEquals(3, strategies.length);
        assertTrue(java.util.Arrays.asList(strategies).contains(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY));
        assertTrue(java.util.Arrays.asList(strategies).contains(
            ADProperties.RoleMapping.MultiGroupStrategy.COMBINE));
        assertTrue(java.util.Arrays.asList(strategies).contains(
            ADProperties.RoleMapping.MultiGroupStrategy.FIRST_MATCH));
    }
}
