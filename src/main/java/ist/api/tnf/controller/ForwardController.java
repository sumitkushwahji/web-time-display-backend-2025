package ist.api.tnf.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForwardController {

    // Handle paths like /something/else
    @RequestMapping(value = "/{x}/**")
    public String redirect() {
        return "forward:/index.html";
    }

    // Handle paths like /home or /about
    @RequestMapping(value = "/{x:[\\w\\-]+}")
    public String redirectSingle() {
        return "forward:/index.html";
    }
}
