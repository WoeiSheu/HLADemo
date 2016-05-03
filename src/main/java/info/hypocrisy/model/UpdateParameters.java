package info.hypocrisy.model;

/**********************
 * Created by Hypocrisy on 4/6/2016.
 * This Class received update parameters.
 **********************/
public class UpdateParameters {
    private String strategy;
    private String mechanism;
    private String step;
    private String lookahead;

    public String getStrategy() {
        return strategy;
    }
    public String getMechanism() {
        return mechanism;
    }
    public String getStep() {
        return step;
    }
    public String getLookahead() {
        return lookahead;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
    public void setMechanism(String mechanism) {
        this.mechanism = mechanism;
    }
    public void setStep(String step) {
        this.step = step;
    }
    public void setLookahead(String lookahead) {
        this.lookahead = lookahead;
    }
}
