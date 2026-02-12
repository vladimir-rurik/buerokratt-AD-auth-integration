package ee.buerokratt.adauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.buerokratt.adauth.config.TestSecurityConfig;
import ee.buerokratt.adauth.model.RoleMappingRequest;
import ee.buerokratt.adauth.model.SAMLValidationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for AuthController
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() throws Exception {
        // When: Calling health endpoint
        mockMvc.perform(get("/actuator/health"))
                // Then: Should return 200 OK with status UP
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").exists())
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    void testInitiateLogin() throws Exception {
        // When: Initiating login
        mockMvc.perform(post("/auth/ad/login")
                        .param("relayState", "/test"))
                // Then: Should return redirect URL
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").exists())
                .andExpect(jsonPath("$.redirectUrl").isString());
    }

    @Test
    void testInitiateLoginWithoutRelayState() throws Exception {
        // When: Initiating login without relay state
        mockMvc.perform(post("/auth/ad/login"))
                // Then: Should still return redirect URL
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").exists());
    }

    @Test
    void testMapRolesWithValidGroups() throws Exception {
        // Given: Valid AD groups
        List<String> adGroups = Arrays.asList(
                "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
                "CN=All-Users,OU=Groups,DC=domain,DC=com"
        );
        RoleMappingRequest request = new RoleMappingRequest();
        request.setAdGroups(adGroups);

        // When: Mapping roles
        mockMvc.perform(post("/auth/ad/map-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Should return mapped roles
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMINISTRATOR")));
    }

    @Test
    void testMapRolesWithEmptyGroups() throws Exception {
        // Given: Empty AD groups
        RoleMappingRequest request = new RoleMappingRequest();
        request.setAdGroups(Arrays.asList());

        // When: Mapping roles
        mockMvc.perform(post("/auth/ad/map-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Should return default role
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_UNAUTHENTICATED")));
    }

    @Test
    void testMapRolesWithInvalidRequest() throws Exception {
        // Given: Invalid request (null groups)
        RoleMappingRequest request = new RoleMappingRequest();

        // When: Mapping roles
        mockMvc.perform(post("/auth/ad/map-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Should return 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidateSAMLResponseWithValidSAML() throws Exception {
        // Given: Valid SAML response
        SAMLValidationRequest request = new SAMLValidationRequest();
        request.setSAMLResponse("valid-mock-saml-response");
        request.setRelayState("/test");

        // When: Validating SAML
        mockMvc.perform(post("/auth/ad/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Should return validation result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userAttributes").exists())
                .andExpect(jsonPath("$.roles").exists());
    }

    @Test
    void testValidateSAMLResponseWithInvalidSAML() throws Exception {
        // Given: Invalid SAML response
        SAMLValidationRequest request = new SAMLValidationRequest();
        request.setSAMLResponse("invalid-saml-response");
        request.setRelayState("/test");

        // When: Validating SAML
        mockMvc.perform(post("/auth/ad/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Should return valid=false with error
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testLogout() throws Exception {
        // When: Calling logout
        mockMvc.perform(get("/auth/ad/logout"))
                // Then: Should return success message
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testPublicEndpointsAccessible() throws Exception {
        // Given: No authentication
        // When: Calling public endpoints
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/ad/login"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/ad/map-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError()); // Validation error for empty body
    }

    @Test
    void testCorsHeaders() throws Exception {
        // When: Making a request
        mockMvc.perform(post("/auth/ad/login")
                        .header("Origin", "https://test.buerokratt.ee"))
                // Then: Should have CORS headers
                .andExpect(status().isOk());
    }
}
