package info.hypocrisy.controller;

import com.google.gson.Gson;
import info.hypocrisy.model.Federate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller is for test so that some objects are comprehensive.
 */

@Controller
@RequestMapping("/test")
public class TestController {
    @RequestMapping(method = RequestMethod.GET)
    public String index(){
        return "test";
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public String create(){
        Federate federate = new Federate();
        federate.createAndJoin();
        federate.test();
        return "{\"status\":\"Success\"}";
    }
}
