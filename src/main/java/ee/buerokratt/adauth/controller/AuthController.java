package ee.buerokratt.adauth.controller;

import ee.buerokratt.adauth.model.*;
import ee.buerokratt.adauth.service.RoleMappingService;
import ee.buerokratt.adauth.service.SAMLService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * REST Controller for AD Authentication
 *
 * Handles SAML authentication flow endpoints
 */
@RestController
@RequestMapping("/auth/ad")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private SAMLService samlService;

    @Autowired
    private RoleMappingService roleMappingService;

    /**
     * Initiate AD authentication
     * Generates SAML AuthnRequest and returns AD FS login URL
     */
    @PostMapping("/login")
    public ResponseEntity<?> initiateLogin(@RequestParam(required = false) String relayState) {
        try {
            log.info("Initiating AD authentication with relayState: {}", relayState);
            String authUrl = samlService.createAuthenticationRequest(relayState);
            return ResponseEntity.ok(Map.of("redirectUrl", authUrl));
        } catch (Exception e) {
            log.error("Failed to initiate AD authentication", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to initiate authentication: " + e.getMessage()));
        }
    }

    /**
     * Validate SAML response and map roles
     * Called by Ruuter after receiving SAML response from AD FS
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateSAMLResponse(@RequestBody SAMLValidationRequest request) {
        try {
            log.info("Validating SAML response");
            ValidationResult validationResult = samlService.validateResponse(request.getSAMLResponse());

            if (!validationResult.isValid()) {
                log.warn("SAML validation failed: {}", validationResult.getError());
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", validationResult.getError()
                ));
            }

            // Map AD groups to roles
            RoleMappingResult roleResult = roleMappingService.mapGroups(
                validationResult.getUserAttributes().getMemberOf()
            );

            log.info("SAML validation successful for user: {}, roles: {}",
                validationResult.getUserAttributes().getUPN(), roleResult.getRoles());

            return ResponseEntity.ok(Map.of(
                "valid", true,
                "userAttributes", validationResult.getUserAttributes(),
                "roles", roleResult.getRoles()
            ));
        } catch (Exception e) {
            log.error("Error processing SAML response", e);
            return ResponseEntity.ok(Map.of(
                "valid", false,
                "error", "Validation failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Map AD groups to roles
     * Separate endpoint for testing and manual role mapping
     */
    @PostMapping("/map-roles")
    public ResponseEntity<?> mapRoles(@RequestBody RoleMappingRequest request) {
        try {
            log.debug("Mapping {} AD groups to roles", request.getAdGroups().size());
            RoleMappingResult result = roleMappingService.mapGroups(request.getAdGroups());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error mapping roles", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Logout and redirect to AD FS signout
     */
    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            log.info("Processing AD logout");
            // TODO: Implement SAML Single Logout
            return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
            ));
        } catch (Exception e) {
            log.error("Error during logout", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
