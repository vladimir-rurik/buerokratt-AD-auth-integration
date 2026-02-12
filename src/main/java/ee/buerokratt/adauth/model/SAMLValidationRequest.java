package ee.buerokratt.adauth.model;

/**
 * SAML Validation Request
 */
public class SAMLValidationRequest {

    private String SAMLResponse;
    private String RelayState;

    public SAMLValidationRequest() {
    }

    public SAMLValidationRequest(String SAMLResponse, String RelayState) {
        this.SAMLResponse = SAMLResponse;
        this.RelayState = RelayState;
    }

    public String getSAMLResponse() {
        return SAMLResponse;
    }

    public void setSAMLResponse(String SAMLResponse) {
        this.SAMLResponse = SAMLResponse;
    }

    public String getRelayState() {
        return RelayState;
    }

    public void setRelayState(String RelayState) {
        this.RelayState = RelayState;
    }
}
