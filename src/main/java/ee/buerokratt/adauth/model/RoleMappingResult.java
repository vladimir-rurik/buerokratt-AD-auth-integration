package ee.buerokratt.adauth.model;

import java.util.List;

/**
 * Result of AD group to role mapping
 *
 * Contains mapped roles based on AD group membership
 */
public class RoleMappingResult {

    private List<String> roles;

    public RoleMappingResult() {
    }

    public RoleMappingResult(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
