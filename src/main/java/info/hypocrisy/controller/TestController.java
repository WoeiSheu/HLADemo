package info.hypocrisy.controller;

import info.hypocrisy.model.Federate;
import info.hypocrisy.model.FederateParameters;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller is for test so that some objects are comprehensive.
 */

@Controller
public class TestController {
    Map<String,Map<String,Federate>> mapFederation = new HashMap<String, Map<String, Federate>>();

    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String index(){
        return "test";
    }

    @RequestMapping(value = "/test/federate", method = RequestMethod.POST)
    @ResponseBody
    public String create(@RequestBody FederateParameters federateParameters){
        String federationName = federateParameters.getFederationName();
        String federateName = federateParameters.getFederateName();
        Federate federate;
        if(mapFederation.containsKey(federationName)) {
            Map<String,Federate> mapFederate = mapFederation.get(federationName);
            if(mapFederate.containsKey(federateName)) {
                return "{\"status\":\"Have created before.\"}";
            } else {
                federate = new Federate(federateParameters);
                federate.createAndJoin();
                mapFederate.put(federateName,federate);
            }
        } else {
            Map<String,Federate> mapFederate = new HashMap<String, Federate>();
            federate = new Federate(federateParameters);
            federate.createAndJoin();
            mapFederate.put(federateName,federate);
            mapFederation.put(federationName,mapFederate);
        }

        return "{\"status\":\"Success\"}";
    }

    @RequestMapping(value = "/test/{federationName}/{federateName}", method = RequestMethod.GET)
    @ResponseBody
    public String run(@PathVariable String federationName, @PathVariable String federateName) {
        Federate federate = mapFederation.get(federationName).get(federateName);
        federate.test();
        return "{\"status\":\"Success\"}";
    }
}
