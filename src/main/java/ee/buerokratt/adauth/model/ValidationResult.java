package ee.buerokratt.adauth.model;

/**
 * Result of SAML response validation
 *
 * Contains validation status, error message (if any),
 * and extracted user attributes
 */
public class ValidationResult {

    private boolean valid;
    private String error;
    private UserAttributes userAttributes;

    public ValidationResult() {
    }

    public ValidationResult(boolean valid, String error, UserAttributes userAttributes) {
        this.valid = valid;
        this.error = error;
        this.userAttributes = userAttributes;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public UserAttributes getUserAttributes() {
        return userAttributes;
    }

    public void setUserAttributes(UserAttributes userAttributes) {
        this.userAttributes = userAttributes;
    }

    public static ValidationResult success(UserAttributes userAttributes) {
        return new ValidationResult(true, null, userAttributes);
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, error, null);
    }
}
