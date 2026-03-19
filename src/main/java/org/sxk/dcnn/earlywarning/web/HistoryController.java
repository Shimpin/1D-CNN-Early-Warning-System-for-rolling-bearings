package org.sxk.dcnn.earlywarning.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;
import org.sxk.dcnn.earlywarning.service.PredictionRecordService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@RequestMapping("/history")
public class HistoryController {

    private final PredictionRecordService recordService;

    public HistoryController(PredictionRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public String list(Model model, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<PredictionRecord> records = recordService.listByUserId(userId);
        model.addAttribute("records", records);
        return "history";
    }
}
