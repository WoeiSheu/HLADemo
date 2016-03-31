package info.hypocrisy.model;

/**
 * Created by Hypocrisy on 3/25/2016.
 * This class is the parameters that passed into FederateController.
 */
public class FederateParameters{
    private String id;
    private String crcAddress;
    private String federationName;
    private String federateName;
    private String strategy;

    public String getId() {
        return id;
    }
    public String getCrcAddress() {
        return crcAddress;
    }
    public String getFederationName() {
        return federationName;
    }
    public String getFederateName() {
        return federateName;
    }
    public String getStrategy() {
        return strategy;
    }

    public void setId(String id) {
        this.id = id;
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
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
