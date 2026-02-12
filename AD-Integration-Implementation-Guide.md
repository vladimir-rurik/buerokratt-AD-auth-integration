# AD Integration - Implementation Guide

## Table of Contents
1. [Quick Start](#quick-start)
2. [AD-Auth Service Implementation](#ad-auth-service-implementation)
3. [Ruuter DSL Files](#ruuter-dsl-files)
4. [Database Changes](#database-changes)
5. [Frontend Changes](#frontend-changes)
6. [Configuration Examples](#configuration-examples)
7. [Testing](#testing)

---

## 1. Quick Start

### Prerequisites

```bash
# Required software
- Java 17+
- Maven 3.8+
- AD FS 2019 or later
- Active Directory Domain Controller
- Docker (for local testing)

# Required services
- TIM (Token Identity Management)
- Resql (Database Service)
- Ruuter (DSL Router)
```

### 5-Minute Setup (Test Environment)

```bash
# 1. Clone repository
git clone https://github.com/buerokratt/ad-auth-service.git
cd ad-auth-service

# 2. Configure application properties
cp src/main/resources/application-example.yml src/main/resources/application.yml

# 3. Generate SAML keystore
./scripts/generate-saml-keystore.sh

# 4. Build application
./mvnw clean package -DskipTests

# 5. Run with Docker
docker-compose up -d

# 6. Test authentication
curl http://localhost:8085/actuator/health
```

---

## 2. AD-Auth Service Implementation

### 2.1 Project Structure

```
ad-auth-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ee/buerokratt/adauth/
│   │   │       ├── AdAuthServiceApplication.java
│   │   │       ├── config/
│   │   │       │   ├── SecurityConfig.java
│   │   │       │   ├── SAMLConfig.java
│   │   │       │   └── ADProperties.java
│   │   │       ├── controller/
│   │   │       │   ├── AuthController.java
│   │   │       │   ├── SAMLController.java
│   │   │       │   └── HealthController.java
│   │   │       ├── service/
│   │   │       │   ├── SAMLService.java
│   │   │       │   ├── RoleMappingService.java
│   │   │       │   └── UserService.java
│   │   │       ├── model/
│   │   │       │   ├── SAMLResponse.java
│   │   │       │   ├── UserAttributes.java
│   │   │       │   └── RoleMapping.java
│   │   │       └── repository/
│   │   │           └── UserRepository.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── saml/
│   │       │   ├── keystore.jks
│   │       │   └── adfs-metadata.xml
│   │       └── ad-role-mapping.yml
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── scripts/
│   ├── generate-saml-keystore.sh
│   └── test-authentication.sh
├── pom.xml
└── README.md
```

### 2.2 Main Application Class

**File:** `src/main/java/ee/buerokratt/adauth/AdAuthServiceApplication.java`

```java
package ee.buerokratt.adauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdAuthServiceApplication.class, args);
    }
}
```

### 2.3 Configuration Properties

**File:** `src/main/java/ee/buerokratt/adauth/config/ADProperties.java`

```java
package ee.buerokratt.adauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ad")
public class ADProperties {

    private Federation federation;
    private RoleMapping roleMapping;
    private Resilience resilience;

    // Getters and Setters

    public static class Federation {
        private String entityId;
        private String acsUrl;
        private String metadataUrl;
        private String keystorePath;
        private String keystorePassword;
        private String privateKeyPassword;
        private Integer timeout = 5000;

        // Getters and Setters
    }

    public static class RoleMapping {
        private List<RoleMappingRule> rules;
        private String defaultRole = "ROLE_UNAUTHENTICATED";
        private MultiGroupStrategy multiGroupStrategy = MultiGroupStrategy.HIGHEST_PRIORITY;

        public enum MultiGroupStrategy {
            HIGHEST_PRIORITY,
            COMBINE,
            FIRST_MATCH
        }

        // Getters and Setters
    }

    public static class RoleMappingRule {
        private String adGroup;
        private String role;
        private Integer priority;

        // Getters and Setters
    }

    public static class Resilience {
        private Integer maxRetries = 3;
        private Long retryBackoff = 1000L;
        private Integer circuitBreakerFailureThreshold = 50;
        private Long circuitBreakerWaitDuration = 30000L;

        // Getters and Setters
    }
}
```

### 2.4 Security Configuration

**File:** `src/main/java/ee/buerokratt/adauth/config/SecurityConfig.java`

```java
package ee.buerokratt.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/readiness").permitAll()
                .requestMatchers("/auth/ad/login").permitAll()
                .requestMatchers("/auth/ad/acs").permitAll()
                .requestMatchers("/auth/ad/metadata").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }
}
```

### 2.5 SAML Configuration

**File:** `src/main/java/ee/buerokratt/adauth/config/SAMLConfig.java`

```java
package ee.buerokratt.adauth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.*;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
public class SAMLConfig {

    @Autowired
    private ADProperties adProperties;

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        return new InMemoryRelyingPartyRegistrationRepository(
            RelyingPartyRegistration.withRegistrationId("adfs")
                .entityId(adProperties.getFederation().getEntityId())
                .assertionConsumerServiceLocation(
                    adProperties.getFederation().getAcsUrl()
                )
                .credentials(c -> c.add(
                    loadSamlCredential()
                ))
                .metadataUri(
                    adProperties.getFederation().getMetadataUrl()
                )
                .build()
        );
    }

    private Saml2X509Credential loadSamlCredential() {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(
                Files.newInputStream(Paths.get(adProperties.getFederation().getKeystorePath())),
                adProperties.getFederation().getKeystorePassword().toCharArray()
            );

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(
                "saml",
                adProperties.getFederation().getPrivateKeyPassword().toCharArray()
            );

            Certificate cert = keyStore.getCertificate("saml");
            X509Certificate x509Cert = (X509Certificate) cert;

            return Saml2X509Credentials.signing(privateKey, x509Cert);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SAML credentials", e);
        }
    }

    @Bean
    public Saml2MetadataFilter saml2MetadataFilter(
            RelyingPartyRegistrationRepository repository) {
        return new Saml2MetadataFilter(
            repository,
            new OpenSamlMetadataResolver()
        );
    }
}
```

### 2.6 Authentication Controller

**File:** `src/main/java/ee/buerokratt/adauth/controller/AuthController.java`

```java
package ee.buerokratt.adauth.controller;

import ee.buerokratt.adauth.model.*;
import ee.buerokratt.adauth.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/auth/ad")
public class AuthController {

    @Autowired
    private SAMLService samlService;

    @Autowired
    private RoleMappingService roleMappingService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> initiateLogin(@RequestParam(required = false) String relayState) {
        try {
            String authUrl = samlService.createAuthenticationRequest(relayState);
            return ResponseEntity.ok(Map.of("redirectUrl", authUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to initiate authentication"));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateSAMLResponse(@RequestBody SAMLValidationRequest request) {
        try {
            ValidationResult validationResult = samlService.validateResponse(
                request.getSamlResponse()
            );

            if (!validationResult.isValid()) {
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", validationResult.getError()
                ));
            }

            // Map AD groups to roles
            RoleMappingResult roleResult = roleMappingService.mapGroups(
                validationResult.getUserAttributes().getMemberOf()
            );

            return ResponseEntity.ok(Map.of(
                "valid", true,
                "userAttributes", validationResult.getUserAttributes(),
                "roles", roleResult.getRoles()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "valid", false,
                "error", "Validation failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/map-roles")
    public ResponseEntity<?> mapRoles(@RequestBody RoleMappingRequest request) {
        try {
            RoleMappingResult result = roleMappingService.mapGroups(
                request.getAdGroups()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        // Invalidate session and redirect to AD FS logout
        String logoutUrl = samlService.createLogoutRequest();
        return ResponseEntity.ok(Map.of("redirectUrl", logoutUrl));
    }
}
```

### 2.7 SAML Service

**File:** `src/main/java/ee/buerokratt/adauth/service/SAMLService.java`

```java
package ee.buerokratt.adauth.service;

import ee.buerokratt.adauth.config.ADProperties;
import ee.buerokratt.adauth.model.*;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.saml2.provider.service.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SAMLService {

    @Autowired
    private ADProperties adProperties;

    public String createAuthenticationRequest(String relayState) {
        String authnRequestId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // Build SAML AuthnRequest URL
        String destination = "https://adfs.domain.com/adfs/ls/";

        return UriComponentsBuilder.fromUriString(destination)
            .queryParam("SAMLRequest", encodeAuthnRequest(authnRequestId, now))
            .queryParam("RelayState", relayState != null ? relayState : "/")
            .build().toUriString();
    }

    public ValidationResult validateResponse(String base64SAMLResponse) {
        try {
            String samlXml = decodeBase64(base64SAMLResponse);

            // Parse SAML response
            Response response = parseSAMLResponse(samlXml);

            // Validate signature
            if (!validateSignature(response)) {
                return new ValidationResult(false, "Invalid signature", null);
            }

            // Validate conditions (time, recipient, etc.)
            if (!validateConditions(response)) {
                return new ValidationResult(false, "SAML conditions validation failed", null);
            }

            // Extract user attributes
            UserAttributes attributes = extractUserAttributes(response);

            return new ValidationResult(true, null, attributes);
        } catch (Exception e) {
            return new ValidationResult(false, "Validation error: " + e.getMessage(), null);
        }
    }

    private UserAttributes extractUserAttributes(Response response) {
        Assertion assertion = response.getAssertions().get(0);
        AttributeStatement attributeStatement = assertion.getSubject()
            .getSubjectConfirmations().get(0)
            .getSubjectConfirmationData();

        Map<String, List<String>> attributes = new HashMap<>();

        assertion.getAttributeStatements().forEach(attrStmt -> {
            attrStmt.getAttributes().forEach(attr -> {
                String name = attr.getName();
                List<String> values = attr.getAttributeValues().stream()
                    .map(xmlObj -> xmlObj.getDOM().getTextContent())
                    .collect(Collectors.toList());
                attributes.put(name, values);
            });
        });

        // Map SAML attributes to UserAttributes
        UserAttributes userAttr = new UserAttributes();
        userAttr.setUPN(attributes.getOrDefault("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn", List.of()).get(0));
        userAttr.setEmail(attributes.getOrDefault("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", List.of()).get(0));
        userAttr.setDisplayName(attributes.getOrDefault("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", List.of()).get(0));
        userAttr.setMemberOf(attributes.getOrDefault("http://schemas.microsoft.com/ws/2008/06/identity/claims/groups", List.of()));

        return userAttr;
    }

    private String encodeAuthnRequest(String requestId, Instant now) {
        // Implementation for encoding AuthnRequest to Base64
        // Using OpenSAML library
        AuthnRequest authnRequest = buildAuthnRequestObject(requestId, now);
        String xml = serializeToXML(authnRequest);
        return Base64.getEncoder().encodeToString(xml.getBytes());
    }

    // Additional helper methods...
}
```

### 2.8 Role Mapping Service

**File:** `src/main/java/ee/buerokratt/adauth/service/RoleMappingService.java`

```java
package ee.buerokratt.adauth.service;

import ee.buerokratt.adauth.config.ADProperties;
import ee.buerokratt.adauth.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleMappingService {

    @Autowired
    private ADProperties adProperties;

    public RoleMappingResult mapGroups(List<String> adGroups) {
        if (adGroups == null || adGroups.isEmpty()) {
            return new RoleMappingResult(
                Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
            );
        }

        List<ADProperties.RoleMappingRule> rules = adProperties.getRoleMapping().getRules();

        switch (adProperties.getRoleMapping().getMultiGroupStrategy()) {
            case HIGHEST_PRIORITY:
                return mapHighestPriority(adGroups, rules);
            case COMBINE:
                return mapCombine(adGroups, rules);
            case FIRST_MATCH:
                return mapFirstMatch(adGroups, rules);
            default:
                return mapHighestPriority(adGroups, rules);
        }
    }

    private RoleMappingResult mapHighestPriority(List<String> adGroups,
                                                  List<ADProperties.RoleMappingRule> rules) {
        // Sort rules by priority (ascending)
        List<ADProperties.RoleMappingRule> sortedRules = rules.stream()
            .sorted(Comparator.comparingInt(ADProperties.RoleMappingRule::getPriority))
            .collect(Collectors.toList());

        for (ADProperties.RoleMappingRule rule : sortedRules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                return new RoleMappingResult(Collections.singletonList(rule.getRole()));
            }
        }

        return new RoleMappingResult(
            Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
        );
    }

    private RoleMappingResult mapCombine(List<String> adGroups,
                                         List<ADProperties.RoleMappingRule> rules) {
        Set<String> roles = new HashSet<>();

        for (ADProperties.RoleMappingRule rule : rules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                roles.add(rule.getRole());
            }
        }

        if (roles.isEmpty()) {
            roles.add(adProperties.getRoleMapping().getDefaultRole());
        }

        return new RoleMappingResult(new ArrayList<>(roles));
    }

    private RoleMappingResult mapFirstMatch(List<String> adGroups,
                                            List<ADProperties.RoleMappingRule> rules) {
        for (ADProperties.RoleMappingRule rule : rules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                return new RoleMappingResult(Collections.singletonList(rule.getRole()));
            }
        }

        return new RoleMappingResult(
            Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
        );
    }
}
```

### 2.9 Model Classes

**File:** `src/main/java/ee/buerokratt/adauth/model/UserAttributes.java`

```java
package ee.buerokratt.adauth.model;

import java.util.List;

public class UserAttributes {
    private String UPN;
    private String email;
    private String displayName;
    private String firstName;
    private String lastName;
    private List<String> memberOf;

    // Constructors
    public UserAttributes() {}

    public UserAttributes(String UPN, String email, String displayName, List<String> memberOf) {
        this.UPN = UPN;
        this.email = email;
        this.displayName = displayName;
        this.memberOf = memberOf;
    }

    // Getters and Setters
    public String getUPN() { return UPN; }
    public void setUPN(String UPN) { this.UPN = UPN; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public List<String> getMemberOf() { return memberOf; }
    public void setMemberOf(List<String> memberOf) { this.memberOf = memberOf; }
}
```

**File:** `src/main/java/ee/buerokratt/adauth/model/ValidationResult.java`

```java
package ee.buerokratt.adauth.model;

public class ValidationResult {
    private boolean valid;
    private String error;
    private UserAttributes userAttributes;

    public ValidationResult(boolean valid, String error, UserAttributes userAttributes) {
        this.valid = valid;
        this.error = error;
        this.userAttributes = userAttributes;
    }

    // Getters
    public boolean isValid() { return valid; }
    public String getError() { return error; }
    public UserAttributes getUserAttributes() { return userAttributes; }
}
```

### 2.10 Application Configuration

**File:** `src/main/resources/application.yml`

```yaml
server:
  port: 8085

spring:
  application:
    name: ad-auth-service

  security:
    saml2:
      relyingparty:
        registration:
          adfs:
            entity-id: https://buerokratt.ee/saml/sp
            acs:
              location: https://buerokratt.ee/auth/ad/acs
              binding: POST
            metadata:
              uri: https://adfs.domain.com/FederationMetadata/2007-06/FederationMetadata.xml

ad:
  federation:
    entity-id: https://buerokratt.ee/saml/sp
    acs-url: https://buerokratt.ee/auth/ad/acs
    metadata-url: https://adfs.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
    keystore-path: /etc/seaml/keystore.jks
    keystore-password: ${SAML_KEYSTORE_PASSWORD:changeit}
    private-key-password: ${SAML_PRIVATE_KEY_PASSWORD:changeit}
    timeout: 5000

  role-mapping:
    default-role: ROLE_UNAUTHENTICATED
    multi-group-strategy: HIGHEST_PRIORITY
    rules:
      - ad-group: CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com
        role: ROLE_ADMINISTRATOR
        priority: 1
      - ad-group: CN=Buerokratt-ServiceManagers,OU=Groups,DC=domain,DC=com
        role: ROLE_SERVICE_MANAGER
        priority: 2
      - ad-group: CN=Buerokratt-CSAgents,OU=Groups,DC=domain,DC=com
        role: ROLE_CUSTOMER_SUPPORT_AGENT
        priority: 3
      - ad-group: CN=Buerokratt-Trainers,OU=Groups,DC=domain,DC=com
        role: ROLE_CHATBOT_TRAINER
        priority: 4
      - ad-group: CN=Buerokratt-Analysts,OU=Groups,DC=domain,DC=com
        role: ROLE_ANALYST
        priority: 5

  resilience:
    max-retries: 3
    retry-backoff: 1000
    circuit-breaker-failure-threshold: 50
    circuit-breaker-wait-duration: 30000

# External service URLs
external-services:
  tim-url: ${TIM_URL:http://localhost:8080}
  resql-url: ${RESQL_URL:http://localhost:8081}

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  health:
    readinessstate:
      enabled: true

# Logging
logging:
  level:
    ee.buerokratt.adauth: DEBUG
    org.springframework.security.saml2: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/ad-auth-service.log
```

---

## 3. Ruuter DSL Files

### 3.1 AD Login Endpoint

**File:** `buerokratt/Ruuter/DSL/GET/auth/ad/login.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "Initiate AD authentication via SAML"
  method: get
  accepts: json
  returns: json
  namespace: authentication
  allowlist:
    headers:
      - field: cookie
        type: string
        description: "Session cookie"
    query:
      - field: returnUrl
        type: string
        description: "URL to redirect after authentication"

check_existing_session:
  call: http.post
  args:
    url: "[#TIM_URL]/jwt/custom-jwt-verify"
    contentType: plaintext
    headers:
      cookie: ${incoming.headers.cookie}
    plaintext: "customJwtCookie"
  result: existing_session
  next: check_session_valid

check_session_valid:
  switch:
    - condition: ${200 <= existing_session.response.statusCodeValue && existing_session.response.statusCodeValue < 300}
      next: already_authenticated
  next: initiate_saml

initiate_saml:
  call: http.post
  args:
    url: "[#AD_AUTH_SERVICE_URL]/auth/ad/login"
    query:
      relayState: ${incoming.query.returnUrl ?? "/"}
    headers:
      cookie: ${incoming.headers.cookie}
  result: saml_redirect
  next: return_redirect

return_redirect:
  status: 302
  headers:
    Location: ${saml_redirect.response.body.redirectUrl}
  return: null
  next: end

already_authenticated:
  status: 302
  headers:
    Location: ${incoming.query.returnUrl ?? "/"}
  return: null
  next: end
```

### 3.2 SAML Assertion Consumer Service

**File:** `buerokratt/Ruuter/DSL/POST/auth/ad/acs.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "SAML ACS endpoint - processes AD authentication response"
  method: post
  accepts: formdata
  returns: json
  namespace: authentication
  allowlist:
    body:
      - field: SAMLResponse
        type: string
        description: "Base64-encoded SAML response"
      - field: RelayState
        type: string
        description: "Return URL after authentication"

validate_saml_response:
  call: http.post
  args:
    url: "[#AD_AUTH_SERVICE_URL]/auth/ad/validate"
    body:
      SAMLResponse: ${incoming.body.SAMLResponse}
      RelayState: ${incoming.body.RelayState}
  result: validation_result
  next: check_validation

check_validation:
  switch:
    - condition: ${validation_result.response.body.valid != true}
      next: return_validation_failed
  next: extract_user_attributes

extract_user_attributes:
  assign:
    user_attributes: ${validation_result.response.body.userAttributes}
    ad_groups: ${user_attributes.memberOf}
    user_upn: ${user_attributes.UPN}
    user_email: ${user_attributes.email}
    user_display_name: ${user_attributes.displayName}
  next: map_groups_to_roles

map_groups_to_roles:
  call: http.post
  args:
    url: "[#AD_AUTH_SERVICE_URL]/auth/ad/map-roles"
    body:
      adGroups: ${ad_groups}
  result: role_mapping
  next: query_or_create_user

query_or_create_user:
  call: http.post
  args:
    url: "[#RESQL_URL]/auth_users/get_or_create_ad_user"
    body:
      upn: ${user_upn}
      email: ${user_email}
      displayName: ${user_display_name}
      authMethod: "AD"
      authorities: ${role_mapping.response.body.roles}
  result: user_record
  next: get_session_length

get_session_length:
  call: http.get
  args:
    url: "[#RESQL_URL]/config/get_configuration"
    query:
      key: "session_length"
  result: session_result
  next: generate_jwt

generate_jwt:
  call: http.post
  args:
    url: "[#TIM_URL]/jwt/custom-jwt-generate"
    body:
      JWTName: "customJwtCookie"
      expirationInMinutes: ${session_result.response.body[0]?.value ?? 480}
      content: {
        "idCode": "${user_upn}",
        "login": "${user_upn}",
        "displayName": "${user_display_name}",
        "email": "${user_email}",
        "firstName": "${user_display_name.split(' ')[0]}",
        "lastName": "${user_display_name.split(' ').slice(-1)[0]}",
        "authorities": ${role_mapping.response.body.roles},
        "authMethod": "AD",
        "adGroups": ${ad_groups}
      }
  result: jwt_result
  next: assign_cookie

assign_cookie:
  assign:
    setCookie:
      customJwtCookie: ${jwt_result.response.body.token}
      Domain: "[#DOMAIN]"
      Path: "/"
      Secure: true
      HttpOnly: true
      SameSite: "Lax"
      Max-Age: ${(session_result.response.body[0]?.value ?? 480) * 60}
  next: redirect_to_application

redirect_to_application:
  status: 302
  headers:
    Set-Cookie: ${setCookie}
    Location: ${incoming.body.RelayState ?? "/"}
  return: null
  next: end

return_validation_failed:
  status: 401
  return: {
    "error": "SAML validation failed",
    "details": ${validation_result.response.body.error}
  }
  next: end
```

### 3.3 AD User Info Template

**File:** `buerokratt/Ruuter/DSL/TEMPLATES/ad.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "Template for getting AD user info from JWT"
  method: post
  accepts: json
  returns: json
  namespace: authentication
  allowlist:
    headers:
      - field: cookie
        type: string

check_cookie:
  switch:
    - condition: ${incoming.headers == null || incoming.headers.cookie == null}
      next: missing_cookie
  next: get_user_info

get_user_info:
  call: http.post
  args:
    url: "[#TIM_URL]/jwt/custom-jwt-userinfo"
    contentType: plaintext
    headers:
      cookie: ${incoming.headers.cookie}
    plaintext: "customJwtCookie"
  result: res
  next: check_response

check_response:
  switch:
    - condition: ${200 <= res.response.statusCodeValue && res.response.statusCodeValue < 300}
      next: return_auth_result
  next: return_bad_request

return_auth_result:
  return: ${res.response.body}
  next: end

return_bad_request:
  status: 400
  return: false
  next: end

missing_cookie:
  status: 401
  return: "no authentication cookie"
  next: end
```

---

## 4. Database Changes

### 4.1 Liquibase Migration

**File:** `db/changelog/2026-02-09-add-ad-auth-support.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <changeSet id="add-ad-auth-columns" author="buerokratt">
        <comment>Add AD authentication support to auth_users table</comment>

        <addColumn tableName="auth_users">
            <column name="auth_method" type="varchar(50)" defaultValue="LOCAL">
                <constraints nullable="false"/>
            </column>
            <column name="ad_upn" type="varchar(255)"/>
            <column name="ad_object_guid" type="varchar(36)"/>
            <column name="ad_last_sync" type="timestamp"/>
        </addColumn>

        <addUniqueConstraint tableName="auth_users"
            columnNames="ad_upn"
            constraintName="uk_auth_users_ad_upn"/>

        <addUniqueConstraint tableName="auth_users"
            columnNames="ad_object_guid"
            constraintName="uk_auth_users_ad_guid"/>

        <createIndex tableName="auth_users"
            indexName="idx_auth_users_ad_upn"
            columnNames="ad_upn"/>

        <createIndex tableName="auth_users"
            indexName="idx_auth_users_ad_guid"
            columnNames="ad_object_guid"/>
    </changeSet>

    <changeSet id="create-ad-users-view" author="buerokratt">
        <comment>Create view for AD users</comment>

        <sql>
            CREATE OR REPLACE VIEW v_ad_users AS
            SELECT
                id, idCode, login, displayName, email,
                firstName, lastName, authorities,
                ad_upn, ad_object_guid, ad_last_sync
            FROM auth_users
            WHERE auth_method = 'AD';
        </sql>
    </changeSet>

    <changeSet id="insert-ad-auth-method-config" author="buerokratt">
        <comment>Add AD to allowed auth methods</comment>

        <sql>
            INSERT INTO config (key, value, description)
            VALUES ('allowed_auth_methods', 'LOCAL,TARA,AD', 'Allowed authentication methods')
            ON CONFLICT (key) DO UPDATE SET value = 'LOCAL,TARA,AD';
        </sql>
    </changeSet>

</databaseChangeLog>
```

### 4.2 Resql DSL: Get or Create AD User

**File:** `Resql/DSL/auth_users/POST/get_or_create_ad_user.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "Get or create AD user"
  method: post
  namespace: authentication

check_existing_user:
  call: http.post
  args:
    url: "[#RESQL_INTERNAL]/auth_users/get_user_by_upn"
    body:
      upn: ${incoming.body.upn}
  result: existing_user
  next: check_user_exists

check_user_exists:
  switch:
    - condition: ${existing_user.response.body && existing_user.response.body.length > 0}
      next: update_existing_user
  next: create_new_user

create_new_user:
  call: http.post
  args:
    url: "[#RESQL_INTERNAL]/auth_users/create_ad_user"
    body:
      upn: ${incoming.body.upn}
      email: ${incoming.body.email}
      displayName: ${incoming.body.displayName}
      authMethod: ${incoming.body.authMethod}
      authorities: ${incoming.body.authorities}
  result: new_user
  next: return_user

update_existing_user:
  call: http.post
  args:
    url: "[#RESQL_INTERNAL]/auth_users/update_ad_user"
    body:
      upn: ${incoming.body.upn}
      email: ${incoming.body.email}
      displayName: ${incoming.body.displayName}
      authorities: ${incoming.body.authorities}
  result: updated_user
  next: return_user

return_user:
  return: ${existing_user.response.body[0] ?? new_user.response.body[0] ?? updated_user.response.body[0]}
  next: end
```

### 4.3 Resql SQL Queries

**File:** `Resql/SQL/auth_users/get_user_by_upn.sql`

```sql
SELECT
    id, idCode, login, displayName, email,
    firstName, lastName, authorities,
    authMethod, ad_upn, ad_object_guid, ad_last_sync
FROM auth_users
WHERE ad_upn = :upn
LIMIT 1;
```

**File:** `Resql/SQL/auth_users/create_ad_user.sql`

```sql
INSERT INTO auth_users (
    idCode, login, displayName, email,
    firstName, lastName, authorities,
    authMethod, ad_upn, ad_last_sync
) VALUES (
    :upn,
    :upn,
    :displayName,
    :email,
    SUBSTRING(:displayName, 1, STRPOS(:displayName, ' ') - 1),
    SUBSTRING(:displayName, STRPOS(:displayName, ' ') + 1),
    :authorities::jsonb,
    'AD',
    :upn,
    CURRENT_TIMESTAMP
)
RETURNING *;
```

**File:** `Resql/SQL/auth_users/update_ad_user.sql`

```sql
UPDATE auth_users
SET
    email = :email,
    displayName = :displayName,
    firstName = SUBSTRING(:displayName, 1, STRPOS(:displayName, ' ') - 1),
    lastName = SUBSTRING(:displayName, STRPOS(:displayName, ' ') + 1),
    authorities = :authorities::jsonb,
    ad_last_sync = CURRENT_TIMESTAMP
WHERE ad_upn = :upn
RETURNING *;
```

---

## 5. Frontend Changes

### 5.1 Authentication Service

**File:** `Authentication-Layer/src/services/ad-authentication.service.ts`

```typescript
import http from './http.service';

class ADAuthenticationService {
  initiateADLogin(returnUrl?: string): Promise<{ redirectUrl: string }> {
    return http.get('/auth/ad/login', {
      params: { returnUrl }
    });
  }

  loginWithAD(returnUrl: string = '/'): Promise<void> {
    return this.initiateADLogin(returnUrl).then(response => {
      if (response.redirectUrl) {
        window.location.href = response.redirectUrl;
      }
    });
  }
}

export default new ADAuthenticationService();
```

### 5.2 Updated Authentication Slice

**File:** `Authentication-Layer/src/slices/authentication.slice.ts` (Updates)

```typescript
// Add new thunks
export const loginWithAD = createAsyncThunk(
  'auth/loginWithAD',
  async (returnUrl: string, thunkApi) => {
    try {
      await ADAuthenticationService.loginWithAD(returnUrl);
      // Redirect happens automatically, so we just dispatch
      thunkApi.dispatch(getUserinfo());
    } catch (error) {
      throw error;
    }
  }
);

// Update state interface
export interface AuthenticationState {
  // ... existing fields
  authMethod: 'LOCAL' | 'TARA' | 'AD' | null;
  adGroups: string[];
}

// Add reducer cases
builder.addCase(loginWithAD.pending, (state) => {
  state.authenticationFailed = false;
});

builder.addCase(loginWithAD.rejected, (state) => {
  state.isAuthenticated = false;
  state.authenticationFailed = true;
  state.authMethod = null;
});

builder.addCase(getUserinfo.fulfilled, (state, action) => {
  // ... existing code
  state.authMethod = action.payload?.authMethod ?? state.authMethod;
  state.adGroups = action.payload?.adGroups ?? [];
});
```

### 5.3 Login Component

**File:** `Authentication-Layer/src/components/Login.tsx`

```typescript
import React, { useState } from 'react';
import { useDispatch } from 'react-redux';
import { loginUser, loginWithTaraJwt, loginWithAD } from '../slices/authentication.slice';
import ADAuthenticationService from '../services/ad-authentication.service';

const LoginComponent: React.FC = () => {
  const dispatch = useDispatch();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');

  const handleLocalLogin = (e: React.FormEvent) => {
    e.preventDefault();
    dispatch(loginUser({ login, pass: password }));
  };

  const handleTARALogin = () => {
    dispatch(loginWithTaraJwt());
  };

  const handleADLogin = () => {
    dispatch(loginWithAD(window.location.pathname));
  };

  return (
    <div className="login-container">
      <h2>Login to Bürokratt</h2>

      <div className="login-methods">
        {/* AD Login Button */}
        <button onClick={handleADLogin} className="btn btn-ad">
          Login with Active Directory
        </button>

        <div className="divider">or</div>

        {/* TARA Login Button */}
        <button onClick={handleTARALogin} className="btn btn-tara">
          Login with TARA (ID-Card)
        </button>

        <div className="divider">or</div>

        {/* Local Login Form */}
        <form onSubmit={handleLocalLogin} className="local-login-form">
          <input
            type="text"
            placeholder="Username"
            value={login}
            onChange={(e) => setLogin(e.target.value)}
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <button type="submit" className="btn btn-local">
            Login
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginComponent;
```

---

## 6. Configuration Examples

### 6.1 Constants Configuration

**File:** `buerokratt/Ruuter/constants.ini`

```ini
[DSL]
# Existing configurations
DOMAIN=buerokratt.ee
CKB_TIM=http://tim:8080
CKB_RESQL=http://resql:8081
CKB_PROJECT_LAYER=http://ruuter:8080

# AD Authentication Service Configuration
AD_AUTH_SERVICE_URL=http://ad-auth-service:8085
TIM_URL=http://tim:8080
RESQL_URL=http://resql:8081

# Cookie Configuration
COOKIE_SAME_SITE=Lax
DOMAIN=.buerokratt.ee
```

### 6.2 Docker Compose (Development)

**File:** `docker-compose.yml`

```yaml
version: '3.8'

services:
  # Existing services
  tim:
    image: buerokratt/tim:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev

  resql:
    image: buerokratt/resql:latest
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev

  ruuter:
    image: buerokratt/ruuter:latest
    ports:
      - "8082:8080"
    volumes:
      - ./Ruuter/DSL:/DSL
      - ./Ruuter/constants.ini:/constants.ini
    environment:
      - SPRING_PROFILES_ACTIVE=dev
    depends_on:
      - tim
      - resql

  # New AD Authentication Service
  ad-auth-service:
    build: ./AD-Auth-Service
    ports:
      - "8085:8085"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - AD_FEDERATION_METADATA_URL=https://adfs.test.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
      - AD_FEDERATION_ENTITY_ID=https://buerokratt.test.ee/saml/sp
      - AD_FEDERATION_ACS_URL=https://buerokratt.test.ee/auth/ad/acs
      - TIM_URL=http://tim:8080
      - RESQL_URL=http://resql:8081
      - SAML_KEYSTORE_PASSWORD=changeit
      - SAML_PRIVATE_KEY_PASSWORD=changeit
    volumes:
      - ./saml/keystore:/etc/seaml
      - ./config:/etc/config
    depends_on:
      - tim
      - resql
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8085/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  authentication-layer:
    image: buerokratt/authentication-layer:latest
    ports:
      - "3000:3000"
    environment:
      - BACKOFFICE_URL=http://localhost:8082
      - NODE_ENV=development
    depends_on:
      - ruuter

networks:
  default:
    name: buerokratt-network
```

---

## 7. Testing

### 7.1 Unit Tests

**File:** `ad-auth-service/src/test/java/ee/buerokratt/adauth/service/RoleMappingServiceTest.java`

```java
package ee.buerokratt.adauth.service;

import ee.buerokratt.adauth.config.ADProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.HIGHEST_PRIORITY
        );

        RoleMappingResult result = roleMappingService.mapGroups(testAdGroups);

        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_ADMINISTRATOR", result.getRoles().get(0));
    }

    @Test
    void testMapCombine() {
        adProperties.getRoleMapping().setMultiGroupStrategy(
            ADProperties.RoleMapping.MultiGroupStrategy.COMBINE
        );

        RoleMappingResult result = roleMappingService.mapGroups(testAdGroups);

        assertTrue(result.getRoles().size() >= 1);
        assertTrue(result.getRoles().contains("ROLE_ADMINISTRATOR"));
    }

    @Test
    void testEmptyGroups() {
        RoleMappingResult result = roleMappingService.mapGroups(Arrays.asList());

        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_UNAUTHENTICATED", result.getRoles().get(0));
    }

    @Test
    void testNoMatchingGroups() {
        List<String> nonMatchingGroups = Arrays.asList(
            "CN=SomeOtherGroup,OU=Groups,DC=domain,DC=com"
        );

        RoleMappingResult result = roleMappingService.mapGroups(nonMatchingGroups);

        assertEquals(1, result.getRoles().size());
        assertEquals("ROLE_UNAUTHENTICATED", result.getRoles().get(0));
    }
}
```

### 7.2 Integration Tests

**File:** `ad-auth-service/src/test/java/ee/buerokratt/adauth/controller/AuthControllerIntegrationTest.java`

```java
package ee.buerokratt.adauth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testInitiateLogin() throws Exception {
        mockMvc.perform(post("/auth/ad/login")
                .param("relayState", "/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl").exists());
    }
}
```

### 7.3 Manual Testing Script

**File:** `scripts/test-authentication.sh`

```bash
#!/bin/bash

# Test AD Authentication Flow

echo "=== AD Authentication Test ==="

# 1. Test health endpoint
echo "1. Testing health endpoint..."
curl -s http://localhost:8085/actuator/health | jq .

# 2. Initiate authentication
echo -e "\n2. Initiating AD authentication..."
AUTH_RESPONSE=$(curl -s -X POST "http://localhost:8085/auth/ad/login?relayState=/test")
REDIRECT_URL=$(echo $AUTH_RESPONSE | jq -r .redirectUrl)
echo "Redirect URL: $REDIRECT_URL"

# 3. Test role mapping
echo -e "\n3. Testing role mapping..."
ROLE_RESPONSE=$(curl -s -X POST http://localhost:8085/auth/ad/map-roles \
  -H "Content-Type: application/json" \
  -d '{
    "adGroups": [
      "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
      "CN=All-Users,OU=Groups,DC=domain,DC=com"
    ]
  }')
echo "Role mapping result:"
echo $ROLE_RESPONSE | jq .

echo -e "\n=== Test Complete ==="
```

---
