package ee.buerokratt.adauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for AD Integration
 *
 * Maps application.yml properties to Java objects
 */
@Configuration
@ConfigurationProperties(prefix = "ad")
public class ADProperties {

    private Federation federation;
    private RoleMapping roleMapping;
    private Resilience resilience;

    // Getters and setters
    public Federation getFederation() {
        return federation;
    }

    public void setFederation(Federation federation) {
        this.federation = federation;
    }

    public RoleMapping getRoleMapping() {
        return roleMapping;
    }

    public void setRoleMapping(RoleMapping roleMapping) {
        this.roleMapping = roleMapping;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public void setResilience(Resilience resilience) {
        this.resilience = resilience;
    }

    public static class Federation {
        private String entityId;
        private String acsUrl;
        private String metadataUrl;
        private String keystorePath;
        private String keystorePassword;
        private String privateKeyPassword;
        private Integer timeout = 5000;

        public String getEntityId() {
            return entityId;
        }

        public void setEntityId(String entityId) {
            this.entityId = entityId;
        }

        public String getAcsUrl() {
            return acsUrl;
        }

        public void setAcsUrl(String acsUrl) {
            this.acsUrl = acsUrl;
        }

        public String getMetadataUrl() {
            return metadataUrl;
        }

        public void setMetadataUrl(String metadataUrl) {
            this.metadataUrl = metadataUrl;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getPrivateKeyPassword() {
            return privateKeyPassword;
        }

        public void setPrivateKeyPassword(String privateKeyPassword) {
            this.privateKeyPassword = privateKeyPassword;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }
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

        public List<RoleMappingRule> getRules() {
            return rules;
        }

        public void setRules(List<RoleMappingRule> rules) {
            this.rules = rules;
        }

        public String getDefaultRole() {
            return defaultRole;
        }

        public void setDefaultRole(String defaultRole) {
            this.defaultRole = defaultRole;
        }

        public MultiGroupStrategy getMultiGroupStrategy() {
            return multiGroupStrategy;
        }

        public void setMultiGroupStrategy(MultiGroupStrategy multiGroupStrategy) {
            this.multiGroupStrategy = multiGroupStrategy;
        }
    }

    public static class RoleMappingRule {
        private String adGroup;
        private String role;
        private Integer priority;

        public String getAdGroup() {
            return adGroup;
        }

        public void setAdGroup(String adGroup) {
            this.adGroup = adGroup;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }
    }

    public static class Resilience {
        private Integer maxRetries = 3;
        private Long retryBackoff = 1000L;
        private Integer circuitBreakerFailureThreshold = 50;
        private Long circuitBreakerWaitDuration = 30000L;

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Long getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Long retryBackoff) {
            this.retryBackoff = retryBackoff;
        }

        public Integer getCircuitBreakerFailureThreshold() {
            return circuitBreakerFailureThreshold;
        }

        public void setCircuitBreakerFailureThreshold(Integer circuitBreakerFailureThreshold) {
            this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        }

        public Long getCircuitBreakerWaitDuration() {
            return circuitBreakerWaitDuration;
        }

        public void setCircuitBreakerWaitDuration(Long circuitBreakerWaitDuration) {
            this.circuitBreakerWaitDuration = circuitBreakerWaitDuration;
        }
    }
}
