package info.hypocrisy.model;

/**
 * Created by Hypocrisy on 2016/4/13.
 */
public class HardwareParameters {
    private String federationName;
    private String federateName;
    private String time;
    private String message;

    public String getFederationName() {
        return federationName;
    }
    public String getFederateName() {
        return federateName;
    }
    public String getTime() {
        return time;
    }
    public String getMessage() {
        return message;
    }

    public void setFederationName(String federationName) {
        this.federationName = federationName;
    }
    public void setFederateName(String federateName) {
        this.federateName = federateName;
    }
    public void setTime(String time) {
        this.time = time;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
