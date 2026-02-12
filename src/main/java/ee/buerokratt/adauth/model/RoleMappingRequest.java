package ee.buerokratt.adauth.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for role mapping
 */
public class RoleMappingRequest implements IRoleMappingRequest {

    @NotEmpty(message = "AD groups list cannot be empty")
    private List<String> adGroups;

    public RoleMappingRequest() {
    }

    public RoleMappingRequest(List<String> adGroups) {
        this.adGroups = adGroups;
    }

    @Override
    public List<String> getAdGroups() {
        return adGroups;
    }

    public void setAdGroups(List<String> adGroups) {
        this.adGroups = adGroups;
    }

    public RoleMappingRequest withAdGroups(List<String> adGroups) {
        this.adGroups = adGroups;
        return this;
    }
}
