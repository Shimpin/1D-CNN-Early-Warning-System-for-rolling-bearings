package org.sxk.dcnn.earlywarning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LM Studio 本地大模型配置，对应 application.properties 中 app.llm.*
 * 文档：https://lmstudio.ai/docs/developer/rest
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** LM Studio 本地服务地址，如 http://127.0.0.1:1234（需先 lms server start） */
    private String apiUrl = "http://127.0.0.1:1234";

    /** API Token（LM Studio 设置中生成，用于 Authorization: Bearer） */
    private String apiKey = "sk-lm-DfOkELN0:TpLtL2Wenb2BV32MYvJ4";

    /** 已加载的模型标识，与 LM Studio 中显示一致，如 qwen/qwen3.5-9b */
    private String model = "default";
}
