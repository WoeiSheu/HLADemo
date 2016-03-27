package info.hypocrisy.controller;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by Gaea on 3/24/2016.
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
        JSONArray locations = new JSONArray();
        locations.put(94043);
        locations.put(90210);
        JSONObject result = new JSONObject("{\"query\":\"Pizza\",\"locations\":[94043,90210],\"prices\":[1000,1600]}");
        //result.put("query", "pizza");
        //result.put("locations", locations);
        return result.toString();
    }
}
