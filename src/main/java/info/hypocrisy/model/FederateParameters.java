package info.hypocrisy.model;

/**
 * Created by Hypocrisy on 3/25/2016.
 * This class is the parameters that passed into FederateController.
 */
public class FederateParameters{
    private String id;
    private String crcAddress;
    private String isPhysicalDevice;
    private String type;
    private String federationName;
    private String federateName;
    private String mechanism;
    private String fomUrl;
    private String strategy;
    private String step;
    private String lookahead;

    public String getId() {
        return id;
    }
    public String getCrcAddress() {
        return crcAddress;
    }
    public String getIsPhysicalDevice() {
        return isPhysicalDevice;
    }
    public String getType() {
        return type;
    }
    public String getFederationName() {
        return federationName;
    }
    public String getFederateName() {
        return federateName;
    }
    public String getMechanism() {
        return mechanism;
    }
    public String getFomUrl() {
        return fomUrl;
    }
    public String getStrategy() {
        return strategy;
    }
    public String getStep() {
        return step;
    }
    public String getLookahead() {
        return lookahead;
    }

    public void setId(String id) {
        this.id = id;
    }
    public void setCrcAddress(String crcAddress) {
        this.crcAddress = crcAddress;
    }
    public void setIsPhysicalDevice(String isPhysicalDevice) {
        this.isPhysicalDevice = isPhysicalDevice;
    }
    public void setType(String type) {
        this.type = type;
    }
    public void setFederationName(String federationName) {
        this.federationName = federationName;
    }
    public void setFederateName(String federateName) {
        this.federateName = federateName;
    }
    public void setMechanism(String mechanism) {
        this.mechanism = mechanism;
    }
    public void setFomUrl(String fomUrl) {
        this.fomUrl = fomUrl;
    }
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
    public void setStep(String step) {
        this.step = step;
    }
    public void setLookahead(String lookahead) {
        this.lookahead = lookahead;
    }
}
