package ee.buerokratt.adauth.model;

import java.util.List;

/**
 * Interface for role mapping requests
 */
public interface IRoleMappingRequest {
    List<String> getAdGroups();
}
