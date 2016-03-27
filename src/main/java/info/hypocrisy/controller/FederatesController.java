package info.hypocrisy.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.deploy.net.HttpRequest;
import info.hypocrisy.model.Federate;
import info.hypocrisy.model.FederateParameters;
import org.json.JSONObject;
import org.springframework.data.repository.query.Parameter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import se.pitch.prti1516e.model.Request;

import javax.inject.Scope;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Gaea on 3/24/2016.
 */
@Controller
@RequestMapping("/federates")
public class FederatesController {
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public String create(HttpSession session, @RequestBody FederateParameters federateParameters) {
        String id = session.getId();
        Map<String,Federate> map = new HashMap<String, Federate>();
        if(map.containsKey(id)) {
            return "{\"status\":\"you have\"}";
        }

        Federate federate = new Federate(federateParameters);
        federate.connect();
        federate.createAndJoin();

        JSONObject result = new JSONObject("{\"status\":\"Success\"}");
        return result.toString();
    }

    @RequestMapping(method = RequestMethod.PUT)
    @ResponseBody
    public String update(@RequestBody FederateParameters federateParameters) {
        Federate federate = new Federate(federateParameters);
        federate.update();

        JSONObject result = new JSONObject("{\"status\":\"Success\"}");
        return result.toString();
    }

    @RequestMapping(method = RequestMethod.DELETE)
    @ResponseBody
    public String destroy(@RequestBody FederateParameters federateParameters) {
        Federate federate = new Federate(federateParameters);
        federate.destroy();

        JSONObject result = new JSONObject("{\"status\":\"Success\"}");
        return result.toString();
    }
}
