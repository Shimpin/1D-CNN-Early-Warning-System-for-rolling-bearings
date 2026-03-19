package org.sxk.dcnn.earlywarning.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class ModelAdvice {

    @ModelAttribute("currentUsername")
    public String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute("username");
        return u != null ? u.toString() : "";
    }
}
