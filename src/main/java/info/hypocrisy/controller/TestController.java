package info.hypocrisy.controller;

import com.google.gson.Gson;
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
        Gson gson = new Gson();
        Integer[] result = {1,2,3,4,5,6,7,8,9};

        return gson.toJson(result);
    }
}
