package org.sxk.dcnn.earlywarning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 应用级配置项，统一管理路径等配置，避免散落在根目录或各处 @Value。
 * 对应 application.properties 中 app.* 前缀。
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 轴承预测：模型与 Python 脚本所在目录（项目内推荐使用 python 目录，如 ${user.dir}/python）
     */
    private String bearingModelDir;

    /**
     * 本机 Python 可执行文件命令（如 python 或 python3）
     */
    private String bearingPythonExecutable = "python";

    /**
     * 上传文件存储目录（项目内推荐使用 data/upload，如 ${user.dir}/data/upload）
     */
    private String uploadDir;

    /**
     * AI 周报输出目录（项目内推荐使用 data/Report）
     */
    private String reportDir;

    /**
     * AI 周报任务 cron 表达式
     */
    private String reportCron = "0 */10 * * * ?";

    /**
     * 是否启用 AI 周报任务
     */
    private Boolean reportEnabled = Boolean.TRUE;

    private static final String USER_DIR = "user.dir";

    /** 上传目录的 Path 形式，便于 Service/Controller 使用；未配置时使用项目内 data/upload */
    public Path getUploadDirPath() {
        if (uploadDir != null && !uploadDir.isEmpty()) {
            return Path.of(uploadDir);
        }
        return Path.of(System.getProperty(USER_DIR, "."), "data", "upload");
    }

    /** AI 周报目录的 Path 形式；未配置时使用项目内 data/Report */
    public Path getReportDirPath() {
        if (reportDir != null && !reportDir.isEmpty()) {
            return Path.of(reportDir);
        }
        return Path.of(System.getProperty(USER_DIR, "."), "data", "Report");
    }

    /** 模型与脚本目录的 Path 形式；未配置时使用项目内 python */
    public Path getBearingModelDirPath() {
        if (bearingModelDir != null && !bearingModelDir.isEmpty()) {
            return Path.of(bearingModelDir);
        }
        return Path.of(System.getProperty(USER_DIR, "."), "python");
    }
}
