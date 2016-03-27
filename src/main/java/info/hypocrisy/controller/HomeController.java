package info.hypocrisy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


/**
 * Created by Gaea on 3/23/2016.
 */
@Controller
@RequestMapping(value = "/")
public class HomeController {
    @RequestMapping(method = RequestMethod.GET)
    public String index() {
        return "index";
    }
    @RequestMapping(method = RequestMethod.POST)
    public String create() {
        return "create";
    }
}
