package ee.buerokratt.adauth.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserAttributes model
 */
class UserAttributesTest {

    private UserAttributes userAttributes;

    @BeforeEach
    void setUp() {
        userAttributes = new UserAttributes();
    }

    @Test
    void testDefaultConstructor() {
        // Given: Default constructor
        // When: Creating instance
        UserAttributes attrs = new UserAttributes();

        // Then: Should create empty object
        assertNotNull(attrs);
        assertNull(attrs.getUPN());
        assertNull(attrs.getEmail());
        assertNull(attrs.getDisplayName());
        assertNull(attrs.getFirstName());
        assertNull(attrs.getLastName());
        assertNull(attrs.getMemberOf());
    }

    @Test
    void testParameterizedConstructor() {
        // Given: All parameters
        String upn = "test@domain.com";
        String email = "test@domain.com";
        String displayName = "Test User";
        String firstName = "Test";
        String lastName = "User";
        List<String> groups = Arrays.asList("CN=TestGroup");

        // When: Creating instance with parameters
        UserAttributes attrs = new UserAttributes(
            upn, email, displayName, firstName, lastName, groups
        );

        // Then: Should set all fields
        assertEquals(upn, attrs.getUPN());
        assertEquals(email, attrs.getEmail());
        assertEquals(displayName, attrs.getDisplayName());
        assertEquals(firstName, attrs.getFirstName());
        assertEquals(lastName, attrs.getLastName());
        assertEquals(groups, attrs.getMemberOf());
    }

    @Test
    void testSettersAndGetters() {
        // Given: User attributes
        String upn = "john.doe@domain.com";
        String email = "john.doe@domain.com";
        String displayName = "John Doe";
        String firstName = "John";
        String lastName = "Doe";
        List<String> groups = Arrays.asList(
            "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"
        );

        // When: Setting all fields
        userAttributes.setUPN(upn);
        userAttributes.setEmail(email);
        userAttributes.setDisplayName(displayName);
        userAttributes.setFirstName(firstName);
        userAttributes.setLastName(lastName);
        userAttributes.setMemberOf(groups);

        // Then: Should get same values
        assertEquals(upn, userAttributes.getUPN());
        assertEquals(email, userAttributes.getEmail());
        assertEquals(displayName, userAttributes.getDisplayName());
        assertEquals(firstName, userAttributes.getFirstName());
        assertEquals(lastName, userAttributes.getLastName());
        assertEquals(groups, userAttributes.getMemberOf());
    }

    @Test
    void testEmptyGroups() {
        // Given: Empty groups list
        userAttributes.setMemberOf(Collections.emptyList());

        // When: Getting groups
        // Then: Should return empty list
        assertNotNull(userAttributes.getMemberOf());
        assertTrue(userAttributes.getMemberOf().isEmpty());
    }

    @Test
    void testNullGroups() {
        // Given: Null groups
        userAttributes.setMemberOf(null);

        // When: Getting groups
        // Then: Should return null
        assertNull(userAttributes.getMemberOf());
    }

    @Test
    void testMultipleGroups() {
        // Given: Multiple AD groups
        List<String> groups = Arrays.asList(
            "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
            "CN=Buerokratt-ServiceManagers,OU=Groups,DC=domain,DC=com",
            "CN=All-Users,OU=Groups,DC=domain,DC=com"
        );
        userAttributes.setMemberOf(groups);

        // When: Getting groups
        // Then: Should return all groups
        assertEquals(3, userAttributes.getMemberOf().size());
        assertTrue(userAttributes.getMemberOf().contains("CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"));
    }
}
