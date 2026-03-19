package org.sxk.dcnn.earlywarning.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 首页：统计图表由前端 Echarts + /api/stats/charts 渲染
 */
@Controller
@RequestMapping("/")
public class IndexController {

    @GetMapping
    public String index(HttpServletRequest request) {
        return "index";
    }
}
