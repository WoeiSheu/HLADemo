package info.hypocrisy.model;

/**
 * Created by Hypocrisy on 3/31/2016.
 * This class is the returning attributes of federate.
 */
public class FederateAttributes {
    private String name;
    private String federation;
    private String crcAddress;
    private int mechanism;          // 0: Time Stepped, 1: Event Driven
    private String fomName;
    private String fomUrl;
    private String strategy;
    private Double time;
    private Boolean status;
    private String step;
    private String lookahead;

    public String getName() {
        return name;
    }
    public String getFederation() {
        return federation;
    }
    public String getCrcAddress() {
        return crcAddress;
    }
    public int getMechanism() {
        return mechanism;
    }
    public String getFomName() {
        return fomName;
    }
    public String getFomUrl() {
        return fomUrl;
    }
    public String getStrategy() {
        return strategy;
    }
    public Double getTime() {
        return time;
    }
    public Boolean getStatus() {
        return status;
    }
    public String getStep() {
        return step;
    }
    public String getLookahead() {
        return lookahead;
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setFederation(String federation) {
        this.federation = federation;
    }
    public void setCrcAddress(String crcAddress) {
        this.crcAddress = crcAddress;
    }
    public void setMechanism(int mechanism) {
        this.mechanism = mechanism;
    }
    public void setFomName(String fomName) {
        this.fomName = fomName;
    }
    public void setFomUrl(String fomUrl) {
        this.fomUrl = fomUrl;
    }
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
    public void setTime(Double time) {
        this.time = time;
    }
    public void setStatus(Boolean status) {
        this.status = status;
    }
    public void setStep(String step) {
        this.step = step;
    }
    public void setLookahead(String lookahead) {
        this.lookahead = lookahead;
    }
}
