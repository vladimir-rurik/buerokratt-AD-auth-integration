# Active Directory Integration Design for Bürokratt

## Executive Summary

This document outlines a secure, scalable Active Directory (AD) integration solution for the Bürokratt ecosystem using **SAML 2.0** as the primary authentication protocol. The solution follows Bürokratt's Ruuter DSL architecture principles and maintains consistency with the existing TARA authentication pattern.

---

## 1. Protocol Selection and Justification

### Recommended Protocol: **SAML 2.0** (Primary)

**Rationale:**
- **Enterprise-standard**: SAML 2.0 is the de facto standard for enterprise AD integration
- **Security**: XML-based signature validation, encryption support, built-in replay protection
- **Mature ecosystem**: Extensive library support (Spring Security SAML, Apache MOD_AUTH_CAS)
- **AD Native**: Microsoft AD FS (Active Directory Federation Services) has native SAML 2.0 support
- **Architecture alignment**: Follows existing TARA SAML integration pattern

**Alternative Protocol: OIDC** (Optional for modern apps)
- More suitable for mobile/single-page applications
- JSON-based, lighter weight
- Requires additional AD configuration (AD FS 2016+ or Azure AD Proxy)

**Not Recommended: LDAP/LDAPs**
- Direct LDAP bind requires storing AD credentials in application
- No single sign-on (SSO) capabilities
- Security risks with credential management
- Not suitable for modern web applications

---

## 2. Architecture Overview

### 2.1 Component Diagram

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Browser   │         │  AD FS 2.0   │         │   AD Domain │
│  (User UI)  │◄───────►│  (IdP/SAML)  │◄───────►│ Controller  │
└─────────────┘         └──────────────┘         └─────────────┘
      │                                                     │
      │ 1. Initiate Auth                                    │
      │
      │ 2. SAML AuthnRequest
      │
      │ 5. SAML Response + Token
      │
      │ 6. POST to Ruuter
      │
      ▼
┌──────────────────────────────────────────────────────────┐
│                    Bürokratt Stack                       │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────┐    │
│  │   Ruuter    │   │  AD-Auth    │   │     TIM      │    │
│  │     DSL     │──►│   Service   │──►│  (JWT Mgmt)  │    │
│  └─────────────┘   └─────────────┘   └──────────────┘    │
│         │                   │                    │       │
│         │                   ▼                    │       │
│  ┌─────────────┐   ┌─────────────┐               │       │
│  │  Role       │   │   Resql     │◄──────────────┘       │
│  │  Mapping    │◄──┤  (User DB)  │   (session, users)    │
│  └─────────────┘   └─────────────┘                       │
└──────────────────────────────────────────────────────────┘
```

### 2.2 Authentication Flow

**Phase 1: SAML Authentication (SP-Initiated)**
1. User accesses Bürokratt application
2. Ruuter detects no valid JWT, redirects to `/auth/ad/login`
3. AD-Auth Service generates SAML AuthnRequest
4. Browser redirects to AD FS login page
5. User authenticates with AD credentials (Kerberos/NTLM)
6. AD FS generates SAML Response (signed assertion)
7. Browser POSTs SAML Response to `/auth/ad/acs` (Assertion Consumer Service)

**Phase 2: Token Generation & Session Creation**
8. Ruuter validates SAML response (signature, timestamp, conditions)
9. Extracts user attributes: UPN, email, displayName,memberOf
10. Queries Resql for local user record (create if not exists)
11. Maps AD groups to Bürokratt roles
12. Calls TIM to generate custom JWT with authorities
13. Sets HttpOnly, Secure, SameSite cookie
14. Redirects to application dashboard

**Phase 3: Authorization**
15. Each subsequent request includes JWT cookie
16. Ruuter validates JWT via TIM `/jwt/custom-jwt-verify`
17. Role-based access control enforced via DSL templates

---

## 3. Component Design

### 3.1 AD-Auth Microservice (New Component)

**Technology Stack:**
- Java 17 + Spring Boot 3.x
- Spring Security SAML2 (Spring Security 6)
- Keystore for SAML signing/verification

**Responsibilities:**
- SAML SP (Service Provider) configuration
- SAML request generation and response validation
- AD group extraction and role mapping
- User provisioning to Resql

**Configuration (application.yml):**
```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          adfs:
            entity-id: https://buerokratt.ee/saml/sp
            acs:
              location: https://buerokratt.ee/auth/ad/acs
              binding: POST
            apmetadata:
              uri: https://adfs.example.com/FederationMetadata/2007-06/FederationMetadata.xml
            credentials:
              - private-key: classpath:saml/key.pem
                certificate: classpath:saml/cert.pem
```

**API Endpoints:**
- `GET /auth/ad/login` - Initiates SAML authentication
- `POST /auth/ad/acs` - SAML Assertion Consumer Service
- `GET /auth/ad/metadata` - SP metadata endpoint
- `POST /auth/ad/logout` - SAML Single Logout

### 3.2 Ruuter DSL Files

#### 3.2.1 AD Login Initiation

**File:** `DSL/GET/auth/ad/login.yml`

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

check_existing_session:
  call: http.post
  args:
    url: "[#TIM]/jwt/custom-jwt-verify"
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
    url: "[#AD_AUTH_SERVICE]/auth/ad/login"
    query:
      RelayState: ${incoming.query.returnUrl ?? "/"}
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

#### 3.2.2 SAML Assertion Consumer Service

**File:** `DSL/POST/auth/ad/acs.yml`

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
    url: "[#AD_AUTH_SERVICE]/auth/ad/validate"
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
    url: "[#AD_AUTH_SERVICE]/auth/ad/map-roles"
    body:
      adGroups: ${ad_groups}
  result: role_mapping
  next: query_or_create_user

query_or_create_user:
  call: http.post
  args:
    url: "[#RESQL]/auth_users/get_or_create_ad_user"
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
    url: "[#RESQL]/config/get_configuration"
    query:
      key: "session_length"
  result: session_result
  next: generate_jwt

generate_jwt:
  call: http.post
  args:
    url: "[#TIM]/jwt/custom-jwt-generate"
    body:
      JWTName: "customJwtCookie"
      expirationInMinutes: ${session_result.response.body[0]?.value ?? 480}
      content: {
        "idCode": "${user_record.response.body[0].idCode}",
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
      Max-Age: ${session_result.response.body[0]?.value ?? 480 * 60}
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
  return: "SAML validation failed"
  next: end
```

#### 3.2.3 Role Mapping Configuration

**File:** `DSL/TEMPLATES/map-ad-groups.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "Template for mapping AD groups to Bürokratt roles"
  method: post
  accepts: json
  returns: json
  namespace: authentication

check_admin_group:
  switch:
    - condition: ${incoming.body.adGroups.some(g => g.includes("CN=Buerokratt-Admins"))}
      next: assign_admin_role
    - condition: ${incoming.body.adGroups.some(g => g.includes("CN=Buerokratt-ServiceManagers"))}
      next: assign_service_manager_role
    - condition: ${incoming.body.adGroups.some(g => g.includes("CN=Buerokratt-CSAgents"))}
      next: assign_csa_role
    - condition: ${incoming.body.adGroups.some(g => g.includes("CN=Buerokratt-Trainers"))}
      next: assign_trainer_role
    - condition: ${incoming.body.adGroups.some(g => g.includes("CN=Buerokratt-Analysts"))}
      next: assign_analyst_role
  next: assign_default_role

assign_admin_role:
  assign:
    roles: ["ROLE_ADMINISTRATOR"]
  next: return_roles

assign_service_manager_role:
  assign:
    roles: ["ROLE_SERVICE_MANAGER"]
  next: return_roles

assign_csa_role:
  assign:
    roles: ["ROLE_CUSTOMER_SUPPORT_AGENT"]
  next: return_roles

assign_trainer_role:
  assign:
    roles: ["ROLE_CHATBOT_TRAINER"]
  next: return_roles

assign_analyst_role:
  assign:
    roles: ["ROLE_ANALYST"]
  next: return_roles

assign_default_role:
  assign:
    roles: ["ROLE_UNAUTHENTICATED"]
  next: return_roles

return_roles:
  return: {
    "roles": ${roles}
  }
  next: end
```

### 3.3 Database Schema Updates (Resql)

**Table: `auth_users`** (Additions)

```sql
ALTER TABLE auth_users
ADD COLUMN auth_method VARCHAR(50) DEFAULT 'LOCAL',
ADD COLUMN ad_upn VARCHAR(255) UNIQUE,
ADD COLUMN ad_object_guid VARCHAR(36) UNIQUE,
ADD COLUMN ad_last_sync TIMESTAMP;

CREATE INDEX idx_auth_users_ad_upn ON auth_users(ad_upn);
CREATE INDEX idx_auth_users_ad_guid ON auth_users(ad_object_guid);

-- Add AD users view
CREATE OR REPLACE VIEW v_ad_users AS
SELECT
    id,
    idCode,
    login,
    displayName,
    email,
    firstName,
    lastName,
    authorities,
    ad_upn,
    ad_object_guid,
    ad_last_sync
FROM auth_users
WHERE auth_method = 'AD';
```

**Resql DSL:** `auth_users/POST/get_or_create_ad_user.yml`

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

### 3.4 Authentication-Layer Updates

**New Service:** `src/services/ad-authentication.service.ts`

```typescript
import http from './http.service';

class ADAuthenticationService {
  initiateADLogin(returnUrl?: string): Promise<void> {
    return http.get('/auth/ad/login', {
      params: { returnUrl }
    });
  }

  handleADCallback(): Promise<void> {
    // SAML callback is handled by Ruuter via form POST
    window.location.href = '/auth/ad/acs';
  }
}

export default new ADAuthenticationService();
```

**Updated Slice:** `src/slices/authentication.slice.ts`

```typescript
// Add new action
export const loginWithAD = createAsyncThunk('auth/loginWithAD', async (returnUrl: string, thunkApi) => {
  await ADAuthenticationService.initiateADLogin(returnUrl);
});

// Update authentication state interface
export interface AuthenticationState {
  // ... existing fields
  authMethod: 'LOCAL' | 'TARA' | 'AD' | null;
  adGroups: string[];
}

// Add reducer case
builder.addCase(loginWithAD.fulfilled, (state) => {
  state.authMethod = 'AD';
  state.authenticationFailed = false;
});
```

---

## 4. Security Measures

### 4.1 SAML Security Configuration

**Signature Validation:**
- Enforce signed SAML responses
- Validate X.509 certificate chain from AD FS
- Certificate pinning to prevent MITM attacks
- Automatic certificate rotation support

**Message Validation:**
- Enforce valid `NotBefore` and `NotOnOrAfter` conditions
- Replay attack prevention (InResponseTo validation)
- Enforce recipient URL matches ACS endpoint
- Validate AudienceRestriction

**Encryption:**
- Optional: Encrypted assertions (AES-256)
- Encrypted NameID elements

### 4.2 Network Security

**TLS Configuration:**
- TLS 1.2+ only for all endpoints
- Strong cipher suites (ECDHE-RSA-AES256-GCM-SHA384)
- HSTS headers enabled
- Certificate validation (no self-signed certs)

**Firewall Rules:**
```
# Allow Bürokratt servers to AD FS
Allow TCP 443 from buerokratt-web-01 to adfs.example.com
Allow TCP 443 from buerokratt-api-01 to adfs.example.com

# Block direct AD access from internet
Deny TCP 389,636 from 0.0.0.0/0 to ad.domain.com
```

### 4.3 Application Security

**Session Security:**
```yaml
# application.yml - TIM configuration
jwt:
  customJwtCookie:
    secure: true
    httpOnly: true
    sameSite: "Lax"
    maxAge: 28800  # 8 hours
```

**CSRF Protection:**
- SAML responses include RelayState validation
- CSRF tokens for state-changing operations

**Rate Limiting:**
```yaml
# Ruuter configuration
rateLimiting:
  authEndpoints:
    - /auth/ad/login: 10 req/min
    - /auth/ad/acs: 30 req/min
```

---

## 5. Role-Based Access Control (RBAC)

### 5.1 AD Group Structure (Recommended)

```
Buerokratt
├── Buerokratt-Admins (Full system access)
│   └── Mapped to: ROLE_ADMINISTRATOR
│   └── Permissions: All operations, user management, system config
│
├── Buerokratt-ServiceManagers
│   └── Mapped to: ROLE_SERVICE_MANAGER
│   └── Permissions: Chat management, analytics, training
│
├── Buerokratt-CSAgents (Customer Support Agents)
│   └── Mapped to: ROLE_CUSTOMER_SUPPORT_AGENT
│   └── Permissions: Chat interactions, handover to human
│
├── Buerokratt-Trainers
│   └── Mapped to: ROLE_CHATBOT_TRAINER
│   └── Permissions: Intent training, response management
│
└── Buerokratt-Analysts
    └── Mapped to: ROLE_ANALYST
    └── Permissions: Read-only analytics, reports
```

### 5.2 Role Mapping Configuration

**Configuration File:** `config/ad-role-mapping.yml`

```yaml
# AD Group to Role Mapping
roleMappings:
  - adGroup: "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"
    role: "ROLE_ADMINISTRATOR"
    priority: 1

  - adGroup: "CN=Buerokratt-ServiceManagers,OU=Groups,DC=domain,DC=com"
    role: "ROLE_SERVICE_MANAGER"
    priority: 2

  - adGroup: "CN=Buerokratt-CSAgents,OU=Groups,DC=domain,DC=com"
    role: "ROLE_CUSTOMER_SUPPORT_AGENT"
    priority: 3

  - adGroup: "CN=Buerokratt-Trainers,OU=Groups,DC=domain,DC=com"
    role: "ROLE_CHATBOT_TRAINER"
    priority: 4

  - adGroup: "CN=Buerokratt-Analysts,OU=Groups,DC=domain,DC=com"
    role: "ROLE_ANALYST"
    priority: 5

# Fallback role if no groups match
defaultRole: "ROLE_UNAUTHENTICATED"

# Multi-group handling (highest priority wins)
multiGroupStrategy: "HIGHEST_PRIORITY"
```

### 5.3 Authorization DSL Template

**File:** `DSL/TEMPLATES/check-ad-user-authority.yml`

```yaml
declaration:
  call: declare
  version: 1.0
  description: "Check if AD-authenticated user has required authority"
  method: post
  accepts: json
  returns: json
  namespace: authentication

get_user_info:
  template: "[#CKB_PROJECT_LAYER]/ad"
  requestType: templates
  headers:
    cookie: ${incoming.headers.cookie}
  result: user_info
  next: verify_auth_method

verify_auth_method:
  switch:
    - condition: ${user_info?.authMethod != 'AD'}
      next: return_wrong_method
  next: check_authority

check_authority:
  switch:
    - condition: ${user_info?.authorities == null || user_info.authorities.length == 0}
      next: return_unauthorized
    - condition: ${incoming.body.requiredRole == null}
      next: return_authorized
    - condition: ${user_info.authorities.includes(incoming.body.requiredRole)}
      next: return_authorized
  next: return_unauthorized

return_authorized:
  return: {
    "authorized": true,
    "user": ${user_info}
  }
  status: 200
  next: end

return_unauthorized:
  return: {
    "authorized": false,
    "requiredAuthority": ${incoming.body.requiredRole ?? "any"},
    "userAuthorities": ${user_info?.authorities ?? []}
  }
  status: 403
  next: end

return_wrong_method:
  return: {
    "authorized": false,
    "reason": "User not authenticated via AD"
  }
  status: 401
  next: end
```

---

## 6. Reliability & Failover

### 6.1 High Availability Architecture

**AD FS Farm Configuration:**
```
Primary AD FS:     adfs01.domain.com (10.0.1.10)
Secondary AD FS:   adfs02.domain.com (10.0.1.11)
Load Balancer:     adfs.domain.com (10.0.1.20)

Health Check:      /adfs/probe
Failover:          Automatic (cluster-aware)
```

**AD Domain Controller Redundancy:**
```
Primary DC:        dc01.domain.com (10.0.1.30)
Secondary DC:      dc02.domain.com (10.0.1.31)
FSMO Roles:        Distributed
```

### 6.2 Application-Level Failover

**Configuration:** `application.yml` (AD-Auth Service)

```yaml
ad:
  federation:
    metadata:
      - uri: https://adfs01.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
        priority: 1
      - uri: https://adfs02.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
        priority: 2
    timeout: 5000  # milliseconds
    retry:
      maxAttempts: 3
      backoff: 1000
```

**Circuit Breaker Pattern:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      adfsMetadata:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
  retry:
    instances:
      adfsAuth:
        max-attempts: 3
        wait-duration: 1s
```

### 6.3 Graceful Degradation

**Fallback Authentication:**

```yaml
# application.yml
buerokratt:
  auth:
    fallback:
      enabled: true
      fallbackToLocal: true
      fallbackMessage: "AD authentication unavailable. Using local authentication."
```

**DSL: Check AD Availability**

```yaml
check_ad_health:
  call: http.get
  args:
    url: "[#AD_AUTH_SERVICE]/health"
    timeout: 3000
  result: health_check
  next: determine_auth_method

determine_auth_method:
  switch:
    - condition: ${health_check.response.statusCodeValue == 200}
      next: use_ad_auth
  next: fallback_to_local
```

---

## 7. Monitoring & Logging

### 7.1 Metrics to Track

**Authentication Metrics:**
```yaml
metrics:
  - name: ad.auth.success
    type: counter
    description: "Successful AD authentications"
    labels: [user, group, role]

  - name: ad.auth.failure
    type: counter
    description: "Failed AD authentications"
    labels: [reason]

  - name: ad.auth.duration
    type: histogram
    description: "AD authentication duration"
    buckets: [100ms, 500ms, 1s, 2s, 5s]

  - name: ad.saml.validation.errors
    type: counter
    description: "SAML validation errors"
    labels: [error_type]

  - name: ad.role.mapping.duration
    type: histogram
    description: "AD group to role mapping duration"
```

### 7.2 Logging Strategy

**Structured Logging (JSON):**

```json
{
  "timestamp": "2026-02-09T10:30:00Z",
  "level": "INFO",
  "service": "ad-auth-service",
  "event": "authentication.success",
  "user": {
    "upn": "john.doe@domain.com",
    "displayName": "John Doe",
    "authMethod": "AD"
  },
  "roles": ["ROLE_ADMINISTRATOR"],
  "adGroups": ["CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"],
  "duration_ms": 1250,
  "saml_id": "_12345678-1234-1234-1234-123456789abc"
}
```

**OpenSearch Integration:**

```yaml
# application.yml
opensearch:
  url: https://opensearch.buerokratt.ee
  index: buerokratt-auth-events
  auth:
    username: ${OPENSEARCH_USER}
    password: ${OPENSEARCH_PASSWORD}
```

### 7.3 Alerting Rules

**Prometheus Alertmanager:**

```yaml
groups:
  - name: ad_authentication
    rules:
      - alert: HighADAuthFailureRate
        expr: rate(ad_auth_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High AD authentication failure rate"
          description: "{{ $value }} failures/sec"

      - alert: ADAuthUnavailability
        expr: up{job="ad-auth-service"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "AD auth service is down"

      - alert: SlowADAuthResponse
        expr: histogram_quantile(0.95, rate(ad_auth_duration_seconds_bucket[5m])) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Slow AD authentication response times"
```

---

## 8. Deployment Configuration

### 8.1 Docker Compose (Development)

```yaml
version: '3.8'

services:
  ad-auth-service:
    build: ./AD-Auth-Service
    ports:
      - "8085:8085"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - AD_FEDERATION_METADATA_URL=https://adfs.test.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
      - TIM_URL=http://tim:8080
      - RESQL_URL=http://resql:8081
    volumes:
      - ./saml/keystore:/etc/saml/keystore
      - ./config:/etc/config
    depends_on:
      - tim
      - resql
    networks:
      - buerokratt-network

networks:
  buerokratt-network:
    external: true
```

### 8.2 Kubernetes (Production)

**Deployment:** `ad-auth-service-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ad-auth-service
  namespace: buerokratt
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ad-auth-service
  template:
    metadata:
      labels:
        app: ad-auth-service
    spec:
      containers:
      - name: ad-auth-service
        image: buerokratt/ad-auth-service:1.0.0
        ports:
        - containerPort: 8085
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: AD_FEDERATION_METADATA_URL
          valueFrom:
            secretKeyRef:
              name: ad-config
              key: metadata-url
        - name: SAML_KEYSTORE_PATH
          value: "/etc/seaml/keystore.jks"
        - name: SAML_KEYSTORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: saml-secrets
              key: keystore-password
        volumeMounts:
        - name: saml-keystore
          mountPath: "/etc/seaml"
          readOnly: true
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8085
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8085
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: saml-keystore
        secret:
          secretName: saml-keystore
          items:
          - key: keystore.jks
            path: keystore.jks
```

**Service:** `ad-auth-service-service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: ad-auth-service
  namespace: buerokratt
spec:
  selector:
    app: ad-auth-service
  ports:
  - protocol: TCP
    port: 8085
    targetPort: 8085
  type: ClusterIP
```

**Secrets:** `ad-config-secret.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ad-config
  namespace: buerokratt
type: Opaque
stringData:
  metadata-url: "https://adfs.domain.com/FederationMetadata/2007-06/FederationMetadata.xml"
  entity-id: "https://buerokratt.ee/saml/sp"
  acs-url: "https://buerokratt.ee/auth/ad/acs"
```

---

## 9. Testing Strategy

### 9.1 Unit Tests

**SAML Validation Tests:**

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class SAMLValidationServiceTest {

    @Autowired
    private SAMLValidationService samlValidationService;

    @Test
    void testValidSAMLResponse() throws Exception {
        String validSAMLResponse = loadTestSAML("valid-response.xml");
        ValidationResult result = samlValidationService.validate(validSAMLResponse);

        assertTrue(result.isValid());
        assertNotNull(result.getUserAttributes());
    }

    @Test
    void testExpiredSAMLResponse() throws Exception {
        String expiredSAML = loadTestSAML("expired-response.xml");
        ValidationResult result = sAMLValidationService.validate(expiredSAML);

        assertFalse(result.isValid());
        assertEquals("Response expired", result.getError());
    }

    @Test
    void testInvalidSignature() throws Exception {
        String tamperedSAML = loadTestSAML("invalid-signature.xml");
        ValidationResult result = sAMLValidationService.validate(tamperedSAML);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("Invalid signature"));
    }
}
```

### 9.2 Integration Tests

**End-to-End Authentication Flow:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ADAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ADClients adClient;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCompleteADAuthenticationFlow() throws Exception {
        // Step 1: Initiate authentication
        mockMvc.perform(get("/auth/ad/login"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().exists("Location"));

        // Step 2: Simulate AD FS callback
        String samlResponse = generateTestSAMLResponse("testuser@domain.com");
        mockMvc.perform(post("/auth/ad/acs")
                .param("SAMLResponse", Base64.getEncoder().encodeToString(samlResponse.getBytes()))
                .param("RelayState", "/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(cookie().exists("customJwtCookie"));

        // Step 3: Verify authentication
        mockMvc.perform(get("/auth/jwt/userinfo")
                .cookie(new Cookie("customJwtCookie", getTestJWT())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authMethod").value("AD"))
            .andExpect(jsonPath("$.authorities").isArray());
    }
}
```

### 9.3 DSL Tests

**Ruuter DSL Testing:**

```yaml
# Test: DSL/tests/auth/ad/login-test.yml

test_name: "AD Login Initiation"
test_description: "Test that AD login redirects to AD FS"

input:
  method: GET
  path: /auth/ad/login
  headers:
    cookie: ""

expected_output:
  status: 302
  headers:
    Location: "https://adfs.domain.com/adfs/oauth2/authorize"
```

---

## 10. Migration Strategy

### 10.1 Phased Rollout

**Phase 1: Infrastructure Setup (Week 1-2)**
- Configure AD FS with Bürokratt Service Provider
- Generate SAML certificates
- Set up AD groups and test users
- Deploy AD-Auth Service in development environment

**Phase 2: Development & Testing (Week 3-4)**
- Implement AD-Auth Service
- Create Ruuter DSL files for AD authentication
- Update database schema
- Write unit and integration tests
- Security audit and penetration testing

**Phase 3: Pilot Deployment (Week 5-6)**
- Deploy to staging environment
- Onboard pilot group (10-20 users)
- Monitor authentication flows
- Gather feedback and fix issues

**Phase 4: Production Rollout (Week 7-8)**
- Deploy to production alongside existing TARA/Local auth
- Enable AD authentication for 25% of users
- Gradually increase to 100%
- Deprecate local authentication (optional)

**Phase 5: Optimization (Week 9+)**
- Monitor performance metrics
- Optimize role mapping rules
- Implement additional security features
- Documentation and training

### 10.2 User Communication

**Email Template:**

```
Subject: New Active Directory Login for Bürokratt

Dear User,

We are pleased to announce that Bürokratt now supports Active Directory
authentication using your existing organizational credentials.

What's changing:
- You can now log in using your Windows/domain credentials
- No separate password to remember
- Automatic access based on your AD group memberships

When:
Starting [DATE], you will see a new "Login with AD" button on the
login page.

Action required:
None! Your existing permissions have been mapped to your AD groups.

Support:
For questions or issues, contact: support@buerokratt.ee

Best regards,
Bürokratt Team
```

---

## 11. Troubleshooting Guide

### 11.1 Common Issues

**Issue: SAML Validation Failed**
```
Symptoms: "SAML validation failed" error after AD login
Root Causes:
  1. Clock skew between AD FS and Bürokratt servers
  2. Invalid or expired certificate
  3. Network connectivity issues

Resolution:
  1. Sync time with NTP: ntpdate pool.ntp.org
  2. Verify certificate: openssl x509 -in cert.pem -text -noout
  3. Test AD FS connectivity: curl -v https://adfs.domain.com/adfs/probe
```

**Issue: Role Mapping Not Working**
```
Symptoms: User authenticated but assigned ROLE_UNAUTHENTICATED
Root Causes:
  1. AD group name mismatch
  2. User not member of any Bürokratt AD groups
  3. Group mapping configuration error

Resolution:
  1. Verify AD groups: dsquery user -samid "john.doe" | dsget user -memberof
  2. Check role mapping logs in OpenSearch
  3. Test mapping manually: POST /auth/ad/map-roles
```

**Issue: Slow Authentication Performance**
```
Symptoms: Login takes > 5 seconds
Root Causes:
  1. AD FS server under high load
  2. Network latency to AD FS
  3. Large number of AD groups

Resolution:
  1. Check AD FS performance counters
  2. Enable connection pooling
  3. Optimize group search filters
```

### 11.2 Debug Mode

**Enable Verbose Logging:**

```yaml
# application.yml
logging:
  level:
    org.springframework.security.saml2: DEBUG
    ee.buerokratt.adauth: DEBUG
    org.opensaml.saml2: DEBUG
```

**SAML Tracer:**
- Browser extension: "SAML-tracer" (Chrome/Firefox)
- Capture SAML requests/responses
- Validate assertions and signatures

---

## 12. Appendix

### 12.1 SAML Request Example

```xml
<samlp:AuthnRequest
    xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
    ID="_12345678-1234-1234-1234-123456789abc"
    Version="2.0"
    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
    AssertionConsumerServiceURL="https://buerokratt.ee/auth/ad/acs"
    Destination="https://adfs.domain.com/adfs/ls/"
    IssueInstant="2026-02-09T10:30:00Z">
  <saml:Issuer xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
    https://buerokratt.ee/saml/sp
  </saml:Issuer>
  <samlp:NameIDPolicy Format="urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"/>
  <samlp:RequestedAuthnContext Comparison="exact">
    <saml:AuthnContextClassRef xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
      urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport
    </saml:AuthnContextClassRef>
  </samlp:RequestedAuthnContext>
</samlp:AuthnRequest>
```

### 12.2 SAML Response Example

```xml
<samlp:Response
    xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
    ID="_response-id"
    InResponseTo="_12345678-1234-1234-1234-123456789abc"
    Version="2.0"
    IssueInstant="2026-02-09T10:30:05Z">
  <saml:Issuer xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
    https://adfs.domain.com/adfs/services/trust
  </saml:Issuer>
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">...</ds:Signature>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">
        john.doe@domain.com
      </saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer"/>
    </saml:Subject>
    <saml:AttributeStatement>
      <saml:Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress">
        <saml:AttributeValue>john.doe@domain.com</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name">
        <saml:AttributeValue>John Doe</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="http://schemas.microsoft.com/ws/2008/06/identity/claims/groups">
        <saml:AttributeValue>CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com</saml:AttributeValue>
        <saml:AttributeValue>CN=All-Users,OU=Groups,DC=domain,DC=com</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
```

### 12.3 References

- **SAML 2.0 Specification:** https://docs.oasis-open.org/security/saml/v2.0/
- **Spring Security SAML:** https://docs.spring.io/spring-security/reference/saml2/index.html
- **AD FS Deployment:** https://docs.microsoft.com/en-us/windows-server/identity/ad-fs/
- **Bürokratt Ruuter DSL:** `/Ruuter/samples/GUIDE.md`
- **TIM API:** `/TIM/README_API.md`

---
