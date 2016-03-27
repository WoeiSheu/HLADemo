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
//@RequestMapping("/federates")
public class FederatesController {
    Map<String,Federate> map = new HashMap<String, Federate>();
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

            map.put(id,federate);

            JSONObject result = new JSONObject("{\"status\":\"Success\"}");
            return result.toString();
        }
    }

    @RequestMapping(value = "/federates/{id}", method = RequestMethod.PUT)
    @ResponseBody
    public String update(@PathVariable String id) {
        Federate federate = (new HashMap<String,Federate>()).get("id");
        federate.update();

        JSONObject result = new JSONObject("{\"status\":\"Success\"}");
        return result.toString();
    }

    @RequestMapping(value = "/federates/{id}",method = RequestMethod.DELETE)
    @ResponseBody
    public String destroy(@PathVariable String id) {
        if(map.containsKey(id)) {
            Federate federate = map.get(id);
            federate.destroy();
            map.remove(id);
            JSONObject result = new JSONObject("{\"status\":\"Success\"}");
            return result.toString();
        } else {
            return "{\"status\":\"Failure\"}";
        }
    }
}
