package info.hypocrisy.model;

/**
 * Created by Hypocrisy on 3/31/2016.
 * This class is the returning attributes of federate.
 */
public class FederateAttributes {
    private String name;
    private String federation;
    private String fom;
    private String strategy;
    private Double time;
    private Boolean status;
    private String step;
    private String lookahead;

    public String getStep() {
        return step;
    }
    public String getLookahead() {
        return lookahead;
    }
    public String getName() {
        return name;
    }
    public String getFederation() {
        return federation;
    }
    public String getFom() {
        return fom;
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

    public void setStep(String step) {
        this.step = step;
    }
    public void setLookahead(String lookahead) {
        this.lookahead = lookahead;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setFederation(String federation) {
        this.federation = federation;
    }
    public void setFom(String fom) {
        this.fom = fom;
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
}
