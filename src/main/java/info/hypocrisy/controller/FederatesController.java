package info.hypocrisy.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.hypocrisy.model.Federate;
import info.hypocrisy.model.FederateParameters;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller: manage federates with actions like CRUD.
 */
@Controller
//@RequestMapping("/federates")
public class FederatesController {
    Map<String,Federate> map = new HashMap<String, Federate>();
    Gson gson = new GsonBuilder().serializeNulls().create();

    private class ResponseValue {
        private String status;
        private double time;

        public ResponseValue() {
            this.status = "Success";
        }
        public ResponseValue(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
        public double getTime() {
            return time;
        }

        public void setStatus(String status) {
            this.status = status;
        }
        public void setTime(double time) {
            this.time = time;
        }
    }

    @RequestMapping(value = "/federates/time/{id}", method = RequestMethod.GET)
    @ResponseBody
    public String getTime(@PathVariable String id) {
        if(map.containsKey(id)) {
            Federate federate = map.get(id);
            double time = federate.getTimeToMoveTo();

            ResponseValue responseValue = new ResponseValue("Success");
            responseValue.setTime(time);
            return gson.toJson(responseValue);
        } else {
            ResponseValue responseValue = new ResponseValue("Failure");
            return gson.toJson(responseValue);
        }
    }

    /*
    @RequestMapping(value = "/federates/{id}", method = RequestMethod.GET)
    @ResponseBody
    public String get(@PathVariable String id) {
        Federate federate = map.get(id);

        ResponseValue responseValue = new ResponseValue("Success");
        return gson.toJson(responseValue);
    }
    */

    @RequestMapping(value = "/federates", method = RequestMethod.POST)
    @ResponseBody
    public String create(@RequestBody FederateParameters federateParameters) {
        String id = federateParameters.getId();
        if(map.containsKey(id)) {
            Federate federate = map.get(id);
            return "{\"status\":\"Have created before.\"}";
        } else {
            Federate federate = new Federate(federateParameters);
            federate.createAndJoin();
            federate.run();

            map.put(id,federate);

            ResponseValue responseValue = new ResponseValue("Success");
            return gson.toJson(responseValue);
        }
    }

    @RequestMapping(value = "/federates", method = RequestMethod.PUT)
    @ResponseBody
    public String update(@RequestBody FederateParameters federateParameters) {
        String id = federateParameters.getId();
        Federate federate = (new HashMap<String,Federate>()).get(id);
        federate.update();

        ResponseValue responseValue = new ResponseValue("Success");
        return gson.toJson(responseValue);
    }

    @RequestMapping(value = "/federates/{id}",method = RequestMethod.DELETE)
    @ResponseBody
    public String destroy(@PathVariable String id) {
        if(map.containsKey(id)) {
            Federate federate = map.get(id);
            federate.destroy();
            map.remove(id);

            ResponseValue responseValue = new ResponseValue("Success");
            return gson.toJson(responseValue);
        } else {
            ResponseValue responseValue = new ResponseValue("Failure");
            return gson.toJson(responseValue);
        }
    }
}
