package com.charlie.ai.api.dto;

import lombok.Data;

/**
 * @description: 智能体配置响应对象
 * @author: Charlie
 * @date: 2026/10/20 18:51
 */
@Data
public class AiAgentConfigResponseDTO {

    /**
     * 智能体ID
     */
    private String agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 智能体描述
     */
    private String agentDesc;

}
