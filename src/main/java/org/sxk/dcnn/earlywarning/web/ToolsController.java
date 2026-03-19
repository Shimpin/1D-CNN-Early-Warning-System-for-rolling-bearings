package org.sxk.dcnn.earlywarning.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tools")
public class ToolsController {

    @GetMapping
    public String tools() {
        return "redirect:/tools/logs";
    }

    @GetMapping("/logs")
    public String logs() {
        return "tools-logs";
    }

    @GetMapping("/assistant")
    public String assistant() {
        return "tools-ai";
    }
}
