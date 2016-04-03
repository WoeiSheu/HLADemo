package info.hypocrisy.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.hypocrisy.model.Federate;
import info.hypocrisy.model.FederateAttributes;
import info.hypocrisy.model.FederateParameters;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller: manage federates with actions like CRUD.
 */
@Controller
//@RequestMapping("/federates")
public class FederatesController {
    Map<String,Map<String,Federate>> mapFederation = new HashMap<String, Map<String, Federate>>();
    Gson gson = new GsonBuilder().serializeNulls().create();

    private class ResponseValue {
        private String status;

        public ResponseValue() {
            this.status = "Success";
        }
        public ResponseValue(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @RequestMapping(value = "/federates", method = RequestMethod.GET)
    @ResponseBody
    public String getAllFederatesAttributes() {
        Set<Map.Entry<String,Map<String,Federate>>> federationsSet = mapFederation.entrySet();
        Iterator<Map.Entry<String,Map<String,Federate>>> iterFederation = federationsSet.iterator();

        //ArrayList<String> federationsName = new ArrayList<String>();
        Map<String,Map<String,FederateAttributes>> federatesAttributesMap = new HashMap<String, Map<String, FederateAttributes>>();
        while (iterFederation.hasNext()) {
            Map.Entry<String, Map<String, Federate>> entry1 = iterFederation.next();
            //federationsName.add(entry.getKey());

            Map<String, Federate> mapFederate = entry1.getValue();
            Set<Map.Entry<String, Federate>> federatesSet = mapFederate.entrySet();
            Iterator<Map.Entry<String, Federate>> iterFederate = federatesSet.iterator();

            Map<String, FederateAttributes> federateAttributesMap = new HashMap<String, FederateAttributes>();
            while (iterFederate.hasNext()) {
                Map.Entry<String, Federate> entry2 = iterFederate.next();
                /**********************
                 * Set federateAttributes
                 **********************/
                FederateAttributes federateAttributes = entry2.getValue().getFederateAttributes();
                federateAttributesMap.put(entry2.getKey(),federateAttributes);
            }
            federatesAttributesMap.put(entry1.getKey(),federateAttributesMap);
        }
        return gson.toJson(federatesAttributesMap);
    }

    @RequestMapping(value = "/federates/time/{federationName}/{federateName}")
    @ResponseBody
    public String getTime(@PathVariable String federationName,@PathVariable String federateName) {
        Federate federate = mapFederation.get(federationName).get(federateName);
        return federate.getTimeToMoveTo().toString();
    }

    @RequestMapping(value = "/federates", method = RequestMethod.POST)
    @ResponseBody
    public String create(@RequestBody FederateParameters federateParameters) {
        String federationName = federateParameters.getFederationName();
        String federateName = federateParameters.getFederateName();

        if(mapFederation.containsKey(federationName)) {
            Map<String,Federate> mapFederate = mapFederation.get(federationName);
            if(mapFederate.containsKey(federateName)) {
                return "{\"status\":\"Have created before.\"}";
            } else {
                Federate federate = new Federate(federateParameters);
                federate.createAndJoin();
                mapFederate.put(federateName,federate);
            }
        } else {
            Map<String,Federate> mapFederate = new HashMap<String, Federate>();
            Federate federate = new Federate(federateParameters);
            federate.createAndJoin();
            mapFederate.put(federateName,federate);
            mapFederation.put(federationName,mapFederate);
        }
        ResponseValue responseValue = new ResponseValue("Success");
        return gson.toJson(responseValue);
    }

    @RequestMapping(value = "/federates/start", method = RequestMethod.POST)
    @ResponseBody
    public void start(@RequestBody FederateParameters federateParameters) {
        Federate federate = mapFederation.get(federateParameters.getFederationName()).get(federateParameters.getFederateName());
        if(federate.getStatus()) {
            Thread thread = new Thread(federate);
            thread.start();
        } else {
            federate.setStatus(true);
        }
    }

    @RequestMapping(value = "/federates/pause", method = RequestMethod.POST)
    @ResponseBody
    public void pause(@RequestBody FederateParameters federateParameters) {
        Federate federate = mapFederation.get(federateParameters.getFederationName()).get(federateParameters.getFederateName());
        federate.setStatus(false);
    }

    @RequestMapping(value = "/federates", method = RequestMethod.PUT)
    @ResponseBody
    public String update(@RequestBody FederateParameters federateParameters) {
        String federationName = federateParameters.getFederationName();
        String federateName = federateParameters.getFederateName();
        if(mapFederation.containsKey(federationName)) {
            if( mapFederation.get(federationName).containsKey(federateName) ) {
                Federate federate = mapFederation.get(federationName).get(federateName);
                federate.update();

                ResponseValue responseValue = new ResponseValue("Success");
                return gson.toJson(responseValue);
            }
        }

        ResponseValue responseValue = new ResponseValue("Failure");
        return gson.toJson(responseValue);
    }

    @RequestMapping(value = "/federates/{federationName}/{federateName}",method = RequestMethod.DELETE)
    @ResponseBody
    public String destroy(@PathVariable String federationName,@PathVariable String federateName) {
        if(mapFederation.containsKey(federationName)) {
            if( mapFederation.get(federationName).containsKey(federateName) ) {
                Federate federate = mapFederation.get(federationName).get(federateName);
                federate.setState(false);
                federate.destroy();

                mapFederation.get(federationName).remove(federateName);
                if(mapFederation.get(federationName).isEmpty()) {
                    mapFederation.remove(federationName);
                }

                ResponseValue responseValue = new ResponseValue("Success");
                return gson.toJson(responseValue);
            } else {
                mapFederation.remove(federationName);
            }
        }

        ResponseValue responseValue = new ResponseValue("Failure");
        return gson.toJson(responseValue);
    }
}
