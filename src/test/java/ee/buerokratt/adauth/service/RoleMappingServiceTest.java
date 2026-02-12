package ee.buerokratt.adauth.service;

import ee.buerokratt.adauth.config.ADProperties;
import ee.buerokratt.adauth.model.RoleMappingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoleMappingService
 */
@SpringBootTest
class RoleMappingServiceTest {

    @Autowired
    private RoleMappingService roleMappingService;

    @Autowired
    private ADProperties adProperties;

    private List<String> testAdGroups;

    @BeforeEach
    void setUp() {
        testAdGroups = Arrays.asList(
            "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
            "CN=All-Users,OU=Groups,DC=domain,DC=com"
        );
    }

    @Test
    void testMapHighestPriority() {
        // Given: HIGHEST_PRIORITY strategy
        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(testAdGroups);

        // Then: Should get highest priority role (Admins = priority 1)
        assertNotNull(result);
        assertNotNull(result.getRoles());
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_ADMINISTRATOR", result.getRoles().get(0));
    }

    @Test
    void testMapCombine() {
        // Given: COMBINE strategy
        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.COMBINE
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(testAdGroups);

        // Then: Should get all matched roles combined
        assertNotNull(result);
        assertNotNull(result.getRoles());
        assertTrue(result.getRoles().size() >= 1);
        assertTrue(result.getRoles().contains("ROLE_ADMINISTRATOR"));
    }

    @Test
    void testEmptyGroups() {
        // Given: Empty groups list
        List<String> emptyGroups = Collections.emptyList();

        // When: Mapping empty groups
        RoleMappingResult result = roleMappingService.mapGroups(emptyGroups);

        // Then: Should get default role
        assertNotNull(result);
        assertNotNull(result.getRoles());
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_UNAUTHENTICATED", result.getRoles().get(0));
    }

    @Test
    void testNullGroups() {
        // Given: Null groups list
        // When: Mapping null groups
        RoleMappingResult result = roleMappingService.mapGroups(null);

        // Then: Should get default role
        assertNotNull(result);
        assertNotNull(result.getRoles());
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_UNAUTHENTICATED", result.getRoles().get(0));
    }

    @Test
    void testNoMatchingGroups() {
        // Given: Groups that don't match any rule
        List<String> nonMatchingGroups = Arrays.asList(
            "CN=SomeOtherGroup,OU=Groups,DC=domain,DC=com"
        );

        // When: Mapping non-matching groups
        RoleMappingResult result = roleMappingService.mapGroups(nonMatchingGroups);

        // Then: Should get default role
        assertNotNull(result);
        assertNotNull(result.getRoles());
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_UNAUTHENTICATED", result.getRoles().get(0));
    }

    @Test
    void testServiceManagerGroup() {
        // Given: User in ServiceManagers group
        List<String> smGroups = Arrays.asList(
            "CN=Buerokratt-ServiceManagers,OU=Groups,DC=domain,DC=com"
        );

        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(smGroups);

        // Then: Should get SERVICE_MANAGER role
        assertNotNull(result);
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_SERVICE_MANAGER", result.getRoles().get(0));
    }

    @Test
    void testCSAgentGroup() {
        // Given: User in CSAgents group
        List<String> csaGroups = Arrays.asList(
            "CN=Buerokratt-CSAgents,OU=Groups,DC=domain,DC=com"
        );

        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(csaGroups);

        // Then: Should get CUSTOMER_SUPPORT_AGENT role
        assertNotNull(result);
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_CUSTOMER_SUPPORT_AGENT", result.getRoles().get(0));
    }

    @Test
    void testTrainerGroup() {
        // Given: User in Trainers group
        List<String> trainerGroups = Arrays.asList(
            "CN=Buerokratt-Trainers,OU=Groups,DC=domain,DC=com"
        );

        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(trainerGroups);

        // Then: Should get CHATBOT_TRAINER role
        assertNotNull(result);
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_CHATBOT_TRAINER", result.getRoles().get(0));
    }

    @Test
    void testAnalystGroup() {
        // Given: User in Analysts group
        List<String> analystGroups = Arrays.asList(
            "CN=Buerokratt-Analysts,OU=Groups,DC=domain,DC=com"
        );

        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(analystGroups);

        // Then: Should get ANALYST role
        assertNotNull(result);
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_ANALYST", result.getRoles().get(0));
    }

    @Test
    void testMultipleGroupsWithHighestPriority() {
        // Given: User in multiple groups, highest priority is Admins
        List<String> multiGroups = Arrays.asList(
            "CN=Buerokratt-Analysts,OU=Groups,DC=domain,DC=com",
            "CN=All-Users,OU=Groups,DC=domain,DC=com",
            "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"
        );

        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        // When: Mapping groups
        RoleMappingResult result = roleMappingService.mapGroups(multiGroups);

        // Then: Should get ADMINISTRATOR role (priority 1 is highest)
        assertNotNull(result);
        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_ADMINISTRATOR", result.getRoles().get(0));
    }
}
