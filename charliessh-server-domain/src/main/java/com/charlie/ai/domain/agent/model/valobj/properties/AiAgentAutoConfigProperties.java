package com.charlie.ai.domain.agent.model.valobj.properties;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/13 8:35
 */
@Data
@ConfigurationProperties(prefix = "ai.agent.config", ignoreInvalidFields = true)
public class AiAgentAutoConfigProperties {

    /**
     * 是否启用AI Agent自动装配
     */
    private Boolean enabled = false;

    private Map<String, AiAgentConfigTableVO> tables;

}
