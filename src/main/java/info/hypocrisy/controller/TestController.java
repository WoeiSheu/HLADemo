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
        return "{\"status\":\"Success\"}";
    }

    @RequestMapping(value = "/test/{federationName}/{federateName}", method = RequestMethod.GET)
    @ResponseBody
    public String run(@PathVariable String federationName, @PathVariable String federateName) {
        return "{\"status\":\"Success\"}";
    }
}
