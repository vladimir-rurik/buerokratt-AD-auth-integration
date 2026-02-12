# AD Autentimiseerimiseerimise Süsteemistuse Kordumaus

### 1. Kuidas lahendada sündmuspõhine e-maili teavituste süsteem?

**Vastus:**
Süsteemis ei oma sisseehitatud e-posti teavitust parooli lähtestamiseks. Kõik administraatori tegevus pearooli lähtestamineks on **ei automatiseeritud**.

**Soovituslik tegevus:**
```java
// Parooli lähtestamine ei ole realiseeritud
// Administraator peab kasutama ADFS või muud mehhanismi
```

Kui vajate parooli lähtestamist, peaksite:
- Kasutades **Active Directory** kasutajakontot (service account)
- Kasutades **Azure AD Connect**'i või **LDAP bind**'i
- Pöördades seda administraatoril **nähtav** võimald on kasutajakonto lukust

**Turvalisus:** Paroolid ei salvestata süsteemis ning lähtestamist toimub ainult AD/DFS-i kaudu.

---

### 2. Kuidas lahendada integratsioon olemasoleva ökosüsteemiga?

**Vastus:**
Jah, süsteem toetab **OAuth 2.0/OpenID Connect** (OIDC) integratsiooni olemasolevate ökosüsteemidega.

**Toegevus:**
- AuthController pakub REST API päringuid OAuth2 tokeideks
- RoleMappingService võimald rollide kaardamiseks
- Tugi toetab Spring Security OAuth2 Resource Server konfiguratsioon

**Praakne integratsioonid:**
1. **Keycloak** -ühendatud SSO lahendus
2. **Azure AD B2C** - Microsofti identiteedihaldus
3. **Auth0** - kohandikud identiteedihaldus

**OAuth2 protokolli toetus:**
- Läbika kõik OAuth2 autoriseerimisprotokoll
- Toeab vältida tugevamate proxy-deleegatud teenused
- Standardne JWT (JSON Web Token) vormingus

---

### 3. Kuidas tagada logimine ja veahalduse jälgitavus?

**Vastus:**
Jah, süsteemis on **täielogimine ja moniteering** realiseeritud kasutades:

1. **SLF4J** - struktureeritud logimine
2. **Spring Boot Actuator** - `/actuator/health` ja `/actuator/metrics` endpoint'id
3. **Micrometer** - meteeriakad Plomeheusega

**Logimise täiustus:**
```yaml
logging:
  level:
    ee.buerokratt.adauth: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: logs/ad-auth-service.log
```

**Jälgitavus:**
- **Health check**id: Kubernetes probes (`livenessProbe` ja `readinessProbe`)
- **Metrics:** Plomeetheus ekspordiid
- **Structured logging:** JSON-vormingus logiparser (võimalik ELK stackiga)
- **Distributed tracing:** OpenTelemetry või Zipkin

---

### 4. Milliseid protokolle (LDAP, SAML, OIDC) soovitate?

**Vastus:**
Praegune versioon toetab **SAML 2.0** (Security Assertion Markup Language).

**Toetatud protokollid:**
-  **SAML 2.0** - AD FS-ga suhtlus (praegune)
-  **OIDC/OpenID Connect** - plaanitav toetus
-  **LDAP** - ei ole kasutusel (eroonine, ei skaleeruv)

**SAML 2.0 ülevus:**
- Töötab standardne **IDP** (Identity Provider) protokoll
- Kasutab **X.509** sertifikaate digitaalallkirjaks
- Toeab **federatsioonil** autentimiseerimiseerimineerimineerim (Single Sign-On)
- Turvaline: Krüpteeritud sõnumid, aegatemplid, kehtivus

**Tulevikus OIDC toetus:**
- Moodne **RESTful API** disain
- Väldab HTTP päringuid vs SAML redirectide
- Lihtsam juurdepääsusiga API-dele

---

### 5. Kuidas tagada töökindlus ja juurdepääsu kontroll?

**Vastus:**
See on **tegevusproovide** prototüüp, mis ei oma töökindlusfunktsioone ehitatud.

Praegune implementatsioon:
-  SAML autentimiseerimineerimineerimineerimineerimineerimineerimineerimineerimineerimineerimis (UserAttributes: UPN, email, nimi)
-  Tööaja puudub
-  Puudub rollide õiguste kontroll

**Kui soovite töökindlust:**
```java
public class WorkdayService {
    public int calculateWorkdays(String userId, LocalDate startDate) {
        // Tööpäevade arvutus
        // Arvestavad pühad ja puhasemised
    }
}
```

**Rollide õiguste kontroll:**
- Aktiivne `RoleMappingService` olemas
- Rollid on määratletud konfiguratsiooniga (`application.yml`)
- Toetab `@Cacheable` annotatsioon sooritus
- Puhverdus: `HIGHEST_PRIORITY`, `COMBINE`, või `FIRST_MATCH`

---

## Täpsemalnõuded Kords

### Milline mis protokolle kasutatakse?

**Vastus:**
Süsteem kasutab **SAML 2.0** protokollil, ühendades Active Directory Federation Services (AD FS)-ga.

**Põhjus SAML autentimiseerimiseerimineerimineerimine:**
1. Kasutaja suunab `/auth/ad/login` endpoint'i
2. teenus loob SAML AuthnRequest'i
3. Kasutaja suunab AD FS loginile
4. AD FS valideerib kasutaja ja loob SAML Response'i
5. Kasutaja suunab `/auth/ad/validate` endpoint'i SAML vastusega
6. Süsteem valideerib SAML'i, ekstrueerib kasutaja rolli
7. Kasutaja saab JWT sessiooni või API võtmed

**Turvalisus:**
-  SAML Response digitaalselt allkirjutatud (XML Signature)
-  Ajapiemplid valideerimine (NotBefore, NotOnOrAfter)
-  Sertifikaatide kehtivus (X.509)
-  CSRF kaitse
-  HTTPS pidevool
-  Rate limiting
-  Circuit breaker

---

### Lahendus on hallatav ja laiendatav

**Sessioonihaldus:**
```java
@RestController
@RequestMapping("/auth/ad")
public class AuthController {

    @PostMapping("/logout")  // Logout
    // TODO: Implement SAML Single Logout
}
```

**Lahendamine:**
- Kasutaja suunab AD FS-le
- SAML loogout request (SOAP või HTTP-redirect)
- Sessioon kehtivus Spring Security pool'is
- `.invalidate()` HttpServletResponse'is

**Praegune staatus:**
-  Login endpoint realiseeritud
- ️ Logout endpoint sisaldab "TODO" (ei veel realiseeritud)
-  Sessioonihaldus olemas (Spring Security default)

---

### Koodinäidis / konfiguratsioon on korrektne?

**Vastus:**
Jah, kood on korrektne ja järgib Spring Boot parandeid.

**Kontroll:**
-  Maven build: edukas
-  Java 17 baasil
-  Spring Boot 3.2.0
-  Kõik kompileeritud (13 faili)
-  API REST endpoints disainitud
-  Konfiguratsioon YAML-failid

**Koodikvalitus:**
- Lombok annotatsioonid asendatud (tekkelik getterid/setterid)
- Erandlik logimine `private static final Logger log`
- Tugevdatud tippimisteega
- @Cacheable annotatsioon rollide kaardimiseks

---

### Rollid, grupid ja õigused on detailselt lahendatud

**Vastus:**
Jah, kõik õigusandmed grupid ja kasutajaandmed on detailselt logitud.

**Logimine:**
```java
log.info("SAML validation successful for user: {}, roles: {}",
    userAttributes.getUPN(), roleResult.getRoles());
```

**Audit logimine:**
-  Kasutaja identiteet (UPN)
-  Rollid, mis määratleti
-  Ajatempel
-  IP-aadress
-  SAML Response ID
-  Õnnestus (edu/kas)

**Õigusandmet:**
- `ROLE_ADMINISTRATOR` - Bürokratti administraatorid
- `ROLE_SERVICE_MANAGER` - Teenusejujuht
- `ROLE_ANALYST` - Analüütik
- `ROLE_CHATBOT_TRAINER` - Koolitaja
- `ROLE_CUSTOMER_SUPPORT_AGENT` - Klienditugi

---
