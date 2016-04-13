package info.hypocrisy.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.hypocrisy.model.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller: manage federates with actions like CRUD.
 */
@Controller
//@RequestMapping("/federates")
public class FederatesController {
    boolean hardwareConnected = false;
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

    @RequestMapping(value = "/federates/time/{federationName}/{federateName}", method = RequestMethod.GET)
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

    @RequestMapping(value = "/federates/fomFile", method = RequestMethod.POST)
    @ResponseBody
    public String uploadFomFile(MultipartHttpServletRequest request, HttpServletResponse response) {
        //FileSystemResource resource = new FileSystemResource("/WEB-INF/assets/config/some.xml");
        Iterator<String> iter = request.getFileNames();
        MultipartFile file = request.getFile(iter.next());
        String fileName = file.getOriginalFilename();
        String rootPath = request.getSession().getServletContext().getRealPath("/WEB-INF/");
        String folder = rootPath + "/assets/config/";
        File serverFile = new File(folder,fileName);
        try {
            BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
            stream.write(file.getBytes());
            stream.close();
        } catch (Exception e) {
            return "{\"status\":\"Failure\"}";
        }

        return gson.toJson(fileName);
    }

    @RequestMapping(value = "/federates/hardware", method = RequestMethod.POST)
    @ResponseBody
    public String processHardware(@RequestBody HardwareParameters hardwareParameters) {
        if(hardwareConnected) {
            Federate federate = mapFederation.get(hardwareParameters.getFederationName()).get(hardwareParameters.getFederateName());
            if(federate.isFirst) {
                federate.setRealTimeOffset(hardwareParameters.getTime());
            } else {
                federate.setRealTime(hardwareParameters.getTime());
            }
            return "{\"status\":\"Success\"}";
        } else {
            return "{\"status\":\"Haven't joined\"}";
        }
    }

    @RequestMapping(value = "/federates/hardware", method = RequestMethod.PUT)
    @ResponseBody
    public String joinHardware(@RequestBody FederateParameters federateParameters) {
        String federationName = federateParameters.getFederationName();
        String federateName = federateParameters.getFederateName();

        Federate federate;
        if(mapFederation.containsKey(federationName)) {
            Map<String, Federate> mapFederate = mapFederation.get(federationName);
            if (mapFederate.containsKey(federateName)) {
                return "{\"status\":\"Have created before.\"}";
            } else {
                federate = new Federate(federateParameters);
                federate.createAndJoin();
                mapFederate.put(federateName, federate);
            }
        } else {
            Map<String, Federate> mapFederate = new HashMap<>();
            federate = new Federate(federateParameters);
            federate.createAndJoin();
            mapFederate.put(federateName,federate);
            mapFederation.put(federationName,mapFederate);
        }
        /*
        Thread thread = new Thread(federate);
        thread.start();
        federate.isFirst = false;
        federate.setStatus(true);
        */
        hardwareConnected = true;

        return "{\"status\":\"Success\"}";
    }

    @RequestMapping(value = "/federates/update/{federationName}/{federateName}", method = RequestMethod.PUT)
    @ResponseBody
    public String update(@PathVariable String federationName, @PathVariable String federateName, @RequestBody UpdateParameters updateParameters) {
        Federate federate = mapFederation.get(federationName).get(federateName);
        federate.update(updateParameters);
        return "{\"status\":\"Success\"}";
    }

    @RequestMapping(value = "/federates/start/{federationName}/{federateName}", method = RequestMethod.PUT)
    @ResponseBody
    public void start(@PathVariable String federationName, @PathVariable String federateName) {
        Federate federate = mapFederation.get(federationName).get(federateName);
        if(federate.isFirst) {
            Thread thread = new Thread(federate);
            thread.start();
            federate.isFirst = false;
        }
        federate.setStatus(true);
    }

    @RequestMapping(value = "/federates/pause/{federationName}/{federateName}", method = RequestMethod.PUT)
    @ResponseBody
    public void pause(@PathVariable String federationName, @PathVariable String federateName) {
        Federate federate = mapFederation.get(federationName).get(federateName);
        federate.setStatus(false);
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

        hardwareConnected = false;
        ResponseValue responseValue = new ResponseValue("Failure");
        return gson.toJson(responseValue);
    }
}
