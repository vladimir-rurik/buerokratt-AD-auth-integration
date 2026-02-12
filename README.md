# AD-Auth Service

Active Directory Authentication Service for Bürokratt using SAML 2.0

## Overview

This service provides SAML 2.0-based Active Directory authentication for the Bürokratt ecosystem. It acts as a Service Provider (SP) that integrates with AD FS (Active Directory Federation Services) to authenticate users against corporate Active Directory.

## Features

-  SAML 2.0 authentication protocol
-  AD group to role mapping
-  Multiple multi-group strategies (HIGHEST_PRIORITY, COMBINE, FIRST_MATCH)
-  Auto-provisioning of AD users
-  Role-based access control (RBAC)
-  Health check endpoints for Kubernetes
-  Metrics and monitoring support
-  Circuit breaker pattern for resilience
-  Caching for performance

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- AD FS 2019+
- Active Directory Domain Controller

### Build

```bash
# Build the application
./mvnw clean package

# Run tests
./mvnw test
```

### Generate SAML Keystore

```bash
# Generate test keystore (DO NOT use in production)
./scripts/generate-saml-keystore.sh
```

### Run Locally

```bash
# Using Maven
./mvnw spring-boot:run

# Using JAR
java -jar target/ad-auth-service-1.0.0.jar
```

### Run with Docker

```bash
# Build and start
docker-compose up -d

# View logs
docker-compose logs -f ad-auth-service

# Test health endpoint
curl http://localhost:8085/actuator/health
```

## API Endpoints

### POST /auth/ad/login
Initiate AD authentication

**Query Parameters:**
- `relayState` (optional): URL to return after authentication

**Response:**
```json
{
  "redirectUrl": "https://adfs.domain.com/adfs/ls/?..."
}
```

### POST /auth/ad/validate
Validate SAML response from AD FS

**Request Body:**
```json
{
  "SAMLResponse": "base64-encoded-saml-response",
  "RelayState": "/return-url"
}
```

**Response:**
```json
{
  "valid": true,
  "userAttributes": {
    "UPN": "john.doe@domain.com",
    "email": "john.doe@domain.com",
    "displayName": "John Doe",
    "memberOf": ["CN=Buerokratt-Admins,..."]
  },
  "roles": ["ROLE_ADMINISTRATOR"]
}
```

### POST /auth/ad/map-roles
Map AD groups to roles (for testing)

**Request Body:**
```json
{
  "adGroups": [
    "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
    "CN=All-Users,OU=Groups,DC=domain,DC=com"
  ]
}
```

**Response:**
```json
{
  "roles": ["ROLE_ADMINISTRATOR"]
}
```

### GET /actuator/health
Health check endpoint

**Response:**
```json
{
  "status": "UP",
  "application": "AD-Auth Service",
  "version": "1.0.0"
}
```

## Configuration

### Application Properties (application.yml)

```yaml
ad:
  federation:
    entity-id: https://buerokratt.ee/saml/sp
    acs-url: https://buerokratt.ee/auth/ad/acs
    metadata-url: https://adfs.domain.com/FederationMetadata/2007-06/FederationMetadata.xml
    keystore-path: /etc/seaml/keystore.jks
    keystore-password: ${SAML_KEYSTORE_PASSWORD}
    private-key-password: ${SAML_PRIVATE_KEY_PASSWORD}

  role-mapping:
    default-role: ROLE_UNAUTHENTICATED
    multi-group-strategy: HIGHEST_PRIORITY
    rules:
      - ad-group: CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com
        role: ROLE_ADMINISTRATOR
        priority: 1
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `AD_FEDERATION_METADATA_URL` | AD FS metadata URL | - |
| `AD_FEDERATION_ENTITY_ID` | SAML entity ID | - |
| `AD_FEDERATION_ACS_URL` | Assertion Consumer Service URL | - |
| `SAML_KEYSTORE_PASSWORD` | Keystore password | `changeit` |
| `SAML_PRIVATE_KEY_PASSWORD` | Private key password | `changeit` |
| `LOG_LEVEL` | Logging level | `DEBUG` |

## Deployment

### Kubernetes

```bash
# Create namespace
kubectl create namespace buerokratt

# Create secrets
kubectl apply -f k8s/secret.yaml

# Create configmap
kubectl apply -f k8s/configmap.yaml

# Deploy service
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Check status
kubectl get pods -n buerokratt -l app=ad-auth-service
```

### Docker

```bash
# Build image
docker build -t buerokratt/ad-auth-service:1.0.0 .

# Run container
docker run -p 8085:8085 \
  -e AD_FEDERATION_METADATA_URL=https://adfs.domain.com/... \
  buerokratt/ad-auth-service:1.0.0
```

## Testing

### Run Tests

```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw verify

# Manual authentication test
./scripts/test-authentication.sh
```

### Test Role Mapping

```bash
curl -X POST http://localhost:8085/auth/ad/map-roles \
  -H "Content-Type: application/json" \
  -d '{
    "adGroups": [
      "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com"
    ]
  }'
```

## Architecture

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

## Monitoring

### Metrics

The service exposes Prometheus metrics on `/actuator/prometheus`

Available metrics:
- `ad_auth_success_total` - Successful authentications
- `ad_auth_failure_total` - Failed authentications
- `ad_auth_duration_seconds` - Authentication duration
- `ad_role_mapping_duration_seconds` - Role mapping duration

### Logging

Structured JSON logging to `logs/ad-auth-service.log`

```json
{
  "timestamp": "2026-02-09T10:30:00Z",
  "level": "INFO",
  "logger": "ee.buerokratt.adauth.controller.AuthController",
  "message": "Initiating AD authentication",
  "thread": "http-nio-8085-exec-1"
}
```

## Troubleshooting

### Common Issues

**SAML validation failed**
- Check time synchronization: `ntpdate pool.ntp.org`
- Verify certificate: `openssl x509 -in cert.pem -text -noout`
- Test AD FS connectivity: `curl -v https://adfs.domain.com/adfs/probe`

**Slow performance**
- Check AD FS server load
- Review number of AD groups
- Enable connection pooling
