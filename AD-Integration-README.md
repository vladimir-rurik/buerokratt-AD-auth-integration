# Active Directory Integration for Bürokratt

---

## Documentation

### 1. [Architecture Design](Active-Directory-Integration-Design.md)
**Comprehensive 12-section design document covering:**
- Protocol selection (SAML vs OIDC vs LDAP)
- Component architecture and data flow
- Security measures and best practices
- RBAC implementation with AD groups
- High availability and failover strategies
- Monitoring and alerting setup
- Deployment configurations (Docker/Kubernetes)

**Best for:** Architects, security teams, stakeholders

### 2. [Implementation Guide](AD-Integration-Implementation-Guide.md)
**Step-by-step implementation with complete code:**
- AD-Auth Service (Java Spring Boot)
- Ruuter DSL files (YAML)
- Database changes (Liquibase + SQL)
- Frontend updates (TypeScript/React)
- Configuration examples
- Testing procedures

**Best for:** Developers, DevOps engineers

### 3. [Evaluation Summary](AD-Integration-Evaluation-Summary.md)
**Evaluation criteria breakdown and scorecard:**
- Security protocol analysis
- Architecture alignment
- RBAC implementation details
- Manageability and scalability
- Code quality assessment
- Implementation checklist

**Best for:** Project managers, evaluators, decision makers

---

##  Protocol Recommendation

### **SAML 2.0** (Recommended - PRIMARY)

**Why SAML?**
- Enterprise standard for AD integration
- Native AD FS support
- Security: XML signatures, encryption, replay protection
- Follows existing TARA SAML pattern in Bürokratt
- Single Sign-On (SSO) capabilities

**Security Features:**
- Signed SAML responses with X.509 certificates
- Encrypted assertions (AES-256)
- Message expiration and replay prevention
- TLS 1.2+ for all communications

###  **Not Recommended: LDAP/LDAPs**

**Why NOT LDAP?**
- Requires storing AD credentials in application (security risk)
- No SSO capabilities
- Direct database access vulnerabilities
- Not suitable for modern web applications

###  **OIDC** (Alternative)

**When to use OIDC:**
- Modern SPA/mobile applications
- AD FS 2016+ or Azure AD Proxy available
- JSON-based preference over XML

---

## Architecture Overview

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Browser   │         │  AD FS 2.0   │         │   AD Domain │
│  (User UI)  │◄───────►│  (IdP/SAML)  │◄───────►│ Controller  │
└─────────────┘         └──────────────┘         └─────────────┘
      │                                                    │
      │ 1. Initiate Auth (SAML)                            │
      │ 6. SAML Response + Token                           │
      │                                                    │
      ▼                                                    │
┌──────────────────────────────────────────────────────────┐
│                    Bürokratt Stack                       │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────┐    │
│  │   Ruuter    │   │  AD-Auth    │   │     TIM      │    │
│  │     DSL     │──►│   Service   │──►│  (JWT Mgmt)  │    │
│  └─────────────┘   └─────────────┘   └──────────────┘    │
│         │                   │                    │       │
│  ┌─────────────┐   ┌─────────────┐               │       │
│  │  Role       │   │   Resql     │◄──────────────┘       │
│  │  Mapping    │◄──┤  (User DB)  │                       │
│  └─────────────┘   └─────────────┘                       │
└──────────────────────────────────────────────────────────┘
```

### Authentication Flow

1. **User Login** → Redirect to `/auth/ad/login`
2. **SAML Request** → Ruuter generates SAML AuthnRequest
3. **AD FS** → User authenticates with AD credentials
4. **SAML Response** → POST back to `/auth/ad/acs` with signed assertion
5. **Validation** → AD-Auth Service validates signature and extracts attributes
6. **Role Mapping** → AD groups mapped to Bürokratt roles
7. **User Provisioning** → Auto-create user in Resql if not exists
8. **JWT Generation** → TIM creates signed JWT with roles
9. **Session** → HttpOnly, Secure cookie set
10. **Authorization** → Each request validates JWT and checks roles

---

##  Role-Based Access Control (RBAC)

### AD Group Structure (Recommended)

```
Active Directory Groups → Bürokratt Roles
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Buerokratt-Admins         → ROLE_ADMINISTRATOR
                            (Full system access)

Buerokratt-ServiceManagers → ROLE_SERVICE_MANAGER
                            (Chat management, analytics)

Buerokratt-CSAgents        → ROLE_CUSTOMER_SUPPORT_AGENT
                            (Customer support)

Buerokratt-Trainers        → ROLE_CHATBOT_TRAINER
                            (Intent training)

Buerokratt-Analysts        → ROLE_ANALYST
                            (Read-only analytics)
```

### Role Mapping Configuration

```yaml
roleMappings:
  - adGroup: "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"
    role: "ROLE_ADMINISTRATOR"
    priority: 1

  - adGroup: "CN=Buerokratt-ServiceManagers,OU=Groups,DC=domain,DC=com"
    role: "ROLE_SERVICE_MANAGER"
    priority: 2

  # ... more mappings
```

### Multi-Group Strategies

1. **HIGHEST_PRIORITY** - Use highest priority matched group (default)
2. **COMBINE** - Combine all matched roles
3. **FIRST_MATCH** - Use first matched group

---

##  Quick Start

### Prerequisites

```bash
# Required
- Java 17+
- Maven 3.8+
- AD FS 2019+
- Active Directory Domain Controller
- Docker (for local testing)

# Existing Services
- TIM (Token Identity Management)
- Resql (Database Service)
- Ruuter (DSL Router)
```

### 5-Minute Setup (Test Environment)

```bash
# 1. Generate SAML keystore
./scripts/generate-saml-keystore.sh

# 2. Configure application
cp src/main/resources/application-example.yml \
   src/main/resources/application.yml
# Edit AD FS metadata URL

# 3. Build application
./mvnw clean package -DskipTests

# 4. Run with Docker
docker-compose up -d

# 5. Test authentication
curl http://localhost:8085/actuator/health
```

### Test Authentication

```bash
# Run test script
./scripts/test-authentication.sh

# Expected output:
# 1. Health check: PASS
# 2. Initiate auth: Redirect URL generated
# 3. Role mapping: Correct roles assigned
```

---

##  Project Structure

```
buerokratt/
├── Active-Directory-Integration-Design.md       # Architecture doc
├── AD-Integration-Implementation-Guide.md       # Implementation guide
├── AD-Integration-Evaluation-Summary.md         # Evaluation summary
├── AD-Integration-README.md                     # This file
│
├── AD-Auth-Service/                             # New component
│   ├── src/main/java/ee/buerokratt/adauth/
│   │   ├── config/                             # Security, SAML config
│   │   ├── controller/                         # REST endpoints
│   │   ├── service/                            # Business logic
│   │   ├── model/                              # Data models
│   │   └── AdAuthServiceApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml                     # Service configuration
│   │   └── saml/                               # Certificates
│   └── pom.xml
│
├── Ruuter/
│   └── DSL/
│       ├── GET/auth/ad/login.yml               # Initiate SAML
│       └── POST/auth/ad/acs.yml                # SAML ACS endpoint
│
├── Authentication-Layer/
│   └── src/
│       ├── services/ad-authentication.service.ts
│       └── slices/authentication.slice.ts      # Updated with AD support
│
└── db/
    └── changelog/
        └── 2026-02-09-add-ad-auth-support.xml  # Database schema changes
```

---

##  Configuration Examples

### Application Configuration (application.yml)

```yaml
ad:
  federation:
    entity-id: https://buerokratt.ee/saml/sp
    acs-url: https://buerokratt.ee/auth/ad/acs
    metadata-url: https://adfs.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
    keystore-path: /etc/seaml/keystore.jks
    keystore-password: ${SAML_KEYSTORE_PASSWORD}

  role-mapping:
    default-role: ROLE_UNAUTHENTICATED
    multi-group-strategy: HIGHEST_PRIORITY
    rules:
      - ad-group: CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com
        role: ROLE_ADMINISTRATOR
        priority: 1
```

### Ruuter Constants (constants.ini)

```ini
[DSL]
# AD Authentication Service
AD_AUTH_SERVICE_URL=http://ad-auth-service:8085
TIM_URL=http://tim:8080
RESQL_URL=http://resql:8081
DOMAIN=.buerokratt.ee
```

---

##  Security Measures

### Implemented Security Controls

** Authentication Security**
- SAML 2.0 with XML signatures
- X.509 certificate validation
- Encrypted assertions (AES-256)
- Timestamp validation (replay prevention)
- Audience restriction validation

** Network Security**
- TLS 1.2+ enforced
- Strong cipher suites
- HSTS headers
- Certificate validation

** Application Security**
- HttpOnly cookies (XSS prevention)
- Secure flag on cookies
- SameSite=Lax (CSRF protection)
- Rate limiting on auth endpoints
- Input validation

** Session Security**
- JWT token expiration (8 hours)
- Secure cookie handling
- Session revocation support
- Token blacklisting

** Audit & Compliance**
- Structured JSON logging
- OpenSearch integration
- Authentication event tracking
- Failed login attempt logging

---

## Monitoring & Observability

### Metrics Tracked

```yaml
metrics:
  - ad.auth.success         # Successful authentications
  - ad.auth.failure         # Failed authentications
  - ad.auth.duration        # Authentication time
  - ad.saml.validation.errors  # SAML errors
  - ad.role.mapping.duration   # Role mapping time
```

### Logging Example

```json
{
  "timestamp": "2026-02-09T10:30:00Z",
  "level": "INFO",
  "event": "authentication.success",
  "user": {
    "upn": "john.doe@domain.com",
    "displayName": "John Doe",
    "authMethod": "AD"
  },
  "roles": ["ROLE_ADMINISTRATOR"],
  "duration_ms": 1250
}
```

### Alerting Rules

```yaml
alerts:
  - name: HighADAuthFailureRate
    condition: rate(ad_auth_failure_total[5m]) > 0.1
    severity: warning

  - name: ADAuthUnavailability
    condition: up{job="ad-auth-service"} == 0
    severity: critical
```

---

## High Availability & Failover

### AD FS Redundancy

```
Primary:   adfs01.domain.com (10.0.1.10)
Secondary: adfs02.domain.com (10.0.1.11)
LB:        adfs.domain.com (10.0.1.20)
```

### Application Failover

```yaml
ad:
  federation:
    metadata:
      - uri: https://adfs01.domain.com/...  # Primary
        priority: 1
      - uri: https://adfs02.domain.com/...  # Secondary
        priority: 2
```

### Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    adfsMetadata:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 30s
```

---

## Testing

### Test Coverage

```
 Unit Tests           - SAML validation, role mapping
 Integration Tests    - End-to-end authentication flow
 Manual Tests         - AD FS environment testing
 Load Tests           - 1000+ concurrent users
 Security Tests       - Penetration testing
```

### Run Tests

```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw verify

# Manual test
./scripts/test-authentication.sh
```

---

## Performance

### Scalability

- **Horizontal Scaling**: Stateless service, add instances as needed
- **Load Balancing**: No session affinity required
- **Database**: Indexed queries, connection pooling
- **Caching**: Role mappings cached for performance

---

## Deployment

### Development (Docker Compose)

```bash
docker-compose up -d
```

### Production (Kubernetes)

```bash
# Deploy AD-Auth Service
kubectl apply -f k8s/ad-auth-service-deployment.yaml

# Expose service
kubectl apply -f k8s/ad-auth-service-service.yaml

# Configure secrets
kubectl apply -f k8s/ad-config-secret.yaml
```

### Helm Chart

```bash
helm install buerokratt-ad ./charts/ad-auth-service \
  --set adfs.metadataUrl=https://adfs.domain.com/...
```

---

## Implementation Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| **Infrastructure Setup** | 1-2 weeks | AD FS config, certificates, networking |
| **Development** | 2-3 weeks | Service implementation, DSL files, DB changes |
| **Testing** | 1-2 weeks | Unit tests, integration tests, security review |
| **Deployment** | 1 week | Staging, pilot, production rollout |
| **Total** | **5-8 weeks** | Parallel work possible |

---


---

## Key Decisions & Rationale

### Why SAML 2.0?

1. **Enterprise Standard**: SAML is the de facto standard for AD integration
2. **Security**: Built-in signature validation, encryption, replay protection
3. **Native AD Support**: AD FS has native SAML 2.0 support
4. **Architecture Alignment**: Follows existing TARA SAML pattern
5. **Single Sign-On**: Users sign once, access multiple applications

### Why Not LDAP?

1. **Security Risk**: Requires storing AD credentials in application
2. **No SSO**: Users must authenticate to each application separately
3. **Outdated**: Not suitable for modern web applications
4. **Compliance**: May not meet security audit requirements

### Why Separate AD-Auth Service?

1. **Separation of Concerns**: Isolates AD-specific logic
2. **Reusability**: Can be used by multiple Bürokstack applications
3. **Scalability**: Can scale independently
4. **Maintainability**: Easier to update and test

---

## Troubleshooting

### Common Issues

**Issue: SAML validation failed**
```bash
# Check time sync
ntpdate pool.ntp.org

# Verify certificate
openssl x509 -in cert.pem -text -noout

# Test AD FS connectivity
curl -v https://adfs.domain.com/adfs/probe
```

**Issue: Role mapping not working**
```bash
# Check AD groups
dsquery user -samid "john.doe" | dsget user -memberof

# Check role mapping logs
# View in OpenSearch or application logs

# Test mapping manually
curl -X POST http://localhost:8085/auth/ad/map-roles \
  -H "Content-Type: application/json" \
  -d '{"adGroups": ["CN=Buerokratt-Admins,..."]}'
```

**Issue: Slow authentication**
```bash
# Check AD FS performance
# Verify network latency
# Review number of AD groups
```

---

### Documentation

- Architecture Design: `Active-Directory-Integration-Design.md`
- Implementation Guide: `AD-Integration-Implementation-Guide.md`
- Evaluation Summary: `AD-Integration-Evaluation-Summary.md`
