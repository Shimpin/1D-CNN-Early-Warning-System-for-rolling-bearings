package org.sxk.dcnn.earlywarning.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 固定问答库：轴承故障基础问题，匹配预设答案，无需对接大模型。
 */
@Service
public class FaqService {

    private static final Map<String, String> FAQ = new LinkedHashMap<>();

    static {
        FAQ.put("滚动轴承常见故障类型", "滚动轴承常见故障类型包括：内圈故障、外圈故障、滚动体故障以及保持架故障。内圈、外圈和滚动体故障多由磨损、疲劳剥落或点蚀引起；保持架故障多为装配或润滑不良导致。");
        FAQ.put("内圈故障产生原因", "内圈故障产生原因主要有：安装不当造成应力集中、润滑不良导致磨损与胶合、过载或冲击载荷、疲劳剥落与点蚀、配合过紧或过松等。");
        FAQ.put("外圈故障产生原因", "外圈故障产生原因包括：外圈与座孔配合不当、润滑不足、杂质进入、过载或偏心载荷、腐蚀与锈蚀等。");
        FAQ.put("滚动体故障产生原因", "滚动体故障产生原因主要有：润滑不良、过载、装配不当、材料缺陷或热处理不当、剥落与点蚀等。");
        FAQ.put("轴承故障如何预警", "本系统基于1D-CNN模型对振动数据进行智能分析，可识别正常、内圈故障、外圈故障、滚动体故障等类型。当检测到故障类型时系统会给出简易预警提示，建议及时检修。");
        FAQ.put("什么是1D-CNN", "1D-CNN即一维卷积神经网络，适用于一维信号（如振动信号）的特征提取与分类，常用于轴承故障诊断。");
        FAQ.put("如何上传数据", "在故障预测页面可选择txt、csv或mat格式的振动数据文件（单文件10MB以内）进行上传，系统将自动调用模型进行分析并返回预测结果与预警建议。");
    }

    /**
     * 根据用户输入匹配预设问题（包含或关键词匹配），返回对应答案；无匹配则返回默认提示。
     */
    public String answer(String question) {
        if (question == null || question.isBlank()) {
            return "请输入您要咨询的问题。";
        }
        String q = question.trim();
        Optional<String> exact = FAQ.entrySet().stream()
            .filter(e -> q.contains(e.getKey()) || e.getKey().contains(q))
            .map(Map.Entry::getValue)
            .findFirst();
        if (exact.isPresent()) return exact.get();
        if (q.contains("故障") || q.contains("轴承")) {
            return "您可以尝试提问：滚动轴承常见故障类型、内圈故障产生原因、外圈故障产生原因、滚动体故障产生原因、轴承故障如何预警等。";
        }
        return "暂未找到与您问题完全匹配的答案，请尝试询问轴承故障类型、故障原因或预警方式等相关问题。";
    }
}
