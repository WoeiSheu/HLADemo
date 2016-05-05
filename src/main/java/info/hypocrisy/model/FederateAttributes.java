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
    private int type;
    private String fomName;
    private String fomUrl;
    private String strategy;
    private Double time;
    private Boolean status;         // Run or pause
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
    public int getType() {
        return type;
    }
    public String getTypeName() {
        String[] typeNames = new String[]{"Cruise Missile","Early-warning Radar","Mission Distribution","Anti-aircraft Missile","Route Planning","Tracking Radar for Enemy Target", "Tracking Radar for Our Target", "Test"};
        return typeNames[this.type];
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
    public void setMechanism(String mechanism) {
        if ("Time Stepped".equals(mechanism)) {
            this.mechanism = 0;
        } else if ("Event Driven".equals(mechanism)) {
            this.mechanism = 1;
        } else {
            this.mechanism = 2;
        }
    }
    public void setType(int type) {
        this.type = type;
    }
    public void setType(String type) {
        switch (type) {
            case "Cruise Missile":
                this.type = 0;
                break;
            case "Early-warning Radar":
                this.type = 1;
                break;
            case "Mission Distribution":
                this.type = 2;
                break;
            case "Anti-aircraft Missile":
                this.type = 3;
                break;
            case "Route Planning":
                this.type = 4;
                break;
            case "Tracking Radar(Enemy)":
                this.type = 5;
                break;
            case "Tracking Radar(Our)":
                this.type = 6;
                break;
            default:
                this.type = 7;
        }
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
