package info.hypocrisy.model;

/**
 * Created by Gaea on 3/25/2016.
 */
public class FederateParameters{
    private String crcAddress;
    private String federationName;
    private String federateName;

    public String getCrcAddress() {
        return crcAddress;
    }
    public String getFederationName() {
        return federationName;
    }
    public String getFederateName() {
        return federateName;
    }

    public void setCrcAddress(String crcAddress) {
        this.crcAddress = crcAddress;
    }
    public void setFederationName(String federationName) {
        this.federationName = federationName;
    }
    public void setFederateName(String federateName) {
        this.federateName = federateName;
    }
}