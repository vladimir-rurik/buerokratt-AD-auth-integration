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
