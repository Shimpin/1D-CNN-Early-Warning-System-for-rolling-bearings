package org.sxk.dcnn.earlywarning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.config.AppProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 调用 Python 脚本对轴承 .mat 文件进行 1D-CNN 故障预测。
 * 模型与脚本路径由 {@link AppProperties} 统一配置（项目内 python 目录）。
 */
@Service
public class BearingPredictService {

    private static final Logger log = LoggerFactory.getLogger(BearingPredictService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PREDICT_SCRIPT_MAT = "predict_single.py";
    private static final String PREDICT_SCRIPT_TXT = "predict_txt.py";

    private final AppProperties appProperties;

    public BearingPredictService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 对指定文件进行预测：.mat 使用 predict_single.py，.txt/.csv 使用 predict_txt.py。
     * 返回 status, message, fault_name, confidence, warning 等。
     */
    public Map<String, Object> predict(Path filePath) {
        Map<String, Object> result = new HashMap<>();
        if (filePath == null || !filePath.toFile().isFile()) {
            result.put("status", "error");
            result.put("message", "文件不存在或不可读");
            return result;
        }
        String name = filePath.getFileName().toString().toLowerCase();
        String scriptName = name.endsWith(".mat") ? PREDICT_SCRIPT_MAT : PREDICT_SCRIPT_TXT;
        Path modelDirPath = appProperties.getBearingModelDirPath();
        Path scriptPath = modelDirPath.resolve(scriptName);
        if (!scriptPath.toFile().isFile()) {
            result.put("status", "error");
            result.put("message", "未找到预测脚本 " + scriptName + "，请确认 app.bearing-model-dir 指向项目内 python 目录");
            return result;
        }

        String absolutePath = filePath.toAbsolutePath().toString();
        String pythonExecutable = appProperties.getBearingPythonExecutable();

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                "-u",  // 无缓冲输出，确保 JSON 立即被 Java 读到
                scriptPath.toAbsolutePath().toString(),
                absolutePath
        );
        pb.redirectErrorStream(true);
        pb.directory(modelDirPath.toFile());
        pb.environment().put("PYTHONUNBUFFERED", "1");
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        try {
            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("status", "error");
                result.put("message", "预测超时（120秒）");
                return result;
            }
            String output = stdout.toString().trim();
            if (output.isEmpty()) {
                result.put("status", "error");
                result.put("message", "Python 脚本无输出");
                return result;
            }
            // Python/TensorFlow 可能先输出 "Python 3.x"、"Using TensorFlow" 等，只解析以 { 开头的 JSON 行
            String jsonLine = extractJsonLine(output);
            if (jsonLine == null) {
                result.put("status", "error");
                String snippet = output.length() > 800 ? output.substring(0, 800) + "..." : output;
                result.put("message", "脚本输出中未找到有效 JSON。请检查 Python 环境与脚本；输出摘要: " + snippet.replace("\n", " "));
                log.warn("Python 脚本原始输出(前800字符): {}", snippet);
                return result;
            }
            JsonNode node = OBJECT_MAPPER.readTree(jsonLine);
            result.put("status", node.has("status") ? node.get("status").asText() : "unknown");
            if (node.has("message")) result.put("message", node.get("message").asText());
            if (node.has("fault_name")) result.put("fault_name", node.get("fault_name").asText());
            if (node.has("load_hp")) result.put("load_hp", node.get("load_hp").asInt());
            if (node.has("fault_size_inch")) result.put("fault_size_inch", node.get("fault_size_inch").asDouble());
            if (node.has("confidence")) result.put("confidence", node.get("confidence").asDouble());
            if (node.has("warning")) result.put("warning", node.get("warning").asBoolean());
            if (node.has("file_path")) result.put("file_path", node.get("file_path").asText());
            return result;
        } catch (Exception e) {
            log.warn("调用 Python 预测失败: {}", e.getMessage());
            result.put("status", "error");
            result.put("message", "预测执行异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 从进程输出中提取唯一一行 JSON（以 { 开头），忽略 Python/TensorFlow 等打印的提示信息。
     */
    private static String extractJsonLine(String output) {
        if (output == null) return null;
        String[] lines = output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("{")) return line;
        }
        return null;
    }
}
