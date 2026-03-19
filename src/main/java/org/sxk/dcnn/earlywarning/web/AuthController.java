package org.sxk.dcnn.earlywarning.web;

import org.sxk.dcnn.earlywarning.config.JwtInterceptor;
import org.sxk.dcnn.earlywarning.service.AuthService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@Controller
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final JwtInterceptor jwtInterceptor;

    public AuthController(AuthService authService, JwtInterceptor jwtInterceptor) {
        this.authService = authService;
        this.jwtInterceptor = jwtInterceptor;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/auth/login")
    @ResponseBody
    public Map<String, Object> login(@RequestParam String username,
                                    @RequestParam String password,
                                    HttpServletResponse response) {
        Map<String, Object> result = authService.login(username, password);
        if (result.get("code").equals(200) && result.get("data") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String token = (String) data.get("token");
            Cookie cookie = new Cookie("token", token);
            cookie.setPath("/");
            cookie.setMaxAge(2 * 3600);
            cookie.setHttpOnly(false);
            response.addCookie(cookie);
            jwtInterceptor.saveTokenToRedis(token);
        }
        return result;
    }

    @PostMapping("/auth/register")
    @ResponseBody
    public Map<String, Object> register(@RequestParam String username,
                                       @RequestParam String password,
                                       @RequestParam String confirmPassword) {
        return authService.register(username, password, confirmPassword);
    }

    @PostMapping("/auth/logout")
    @ResponseBody
    public Map<String, Object> logout(@RequestParam(required = false) String token,
                                     HttpServletResponse response) {
        if (token != null && !token.isEmpty()) {
            jwtInterceptor.removeTokenFromRedis(token);
        }
        Cookie cookie = new Cookie("token", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return java.util.Map.of("code", 200, "msg", "已退出", "data", "");
    }
}
