package ee.buerokratt.adauth.model;

import java.util.List;

/**
 * User attributes extracted from SAML assertion
 *
 * Contains user information retrieved from Active Directory
 * via SAML authentication response
 */
public class UserAttributes {

    private String UPN;              // User Principal Name (e.g., john.doe@domain.com)
    private String email;             // User email address
    private String displayName;        // Full display name
    private String firstName;          // First name
    private String lastName;           // Last name
    private List<String> memberOf;     // AD groups (Distinguished Names)

    public UserAttributes() {
    }

    public UserAttributes(String UPN, String email, String displayName, String firstName, String lastName, List<String> memberOf) {
        this.UPN = UPN;
        this.email = email;
        this.displayName = displayName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.memberOf = memberOf;
    }

    public String getUPN() {
        return UPN;
    }

    public void setUPN(String UPN) {
        this.UPN = UPN;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public List<String> getMemberOf() {
        return memberOf;
    }

    public void setMemberOf(List<String> memberOf) {
        this.memberOf = memberOf;
    }
}
