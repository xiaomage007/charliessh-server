package com.charlie.ai.domain.agent.model.valobj;

import com.google.adk.runner.InMemoryRunner;
import lombok.Builder;
import lombok.Getter;

/**
 * @description: Ai Agent 智能体注册值对象
 * @author: Charlie
 * @date: 2026/10/14 9:34
 */
@Getter
@Builder
//@AllArgsConstructor
//@NoArgsConstructor
public class AiAgentRegisterVO {
    /**
     * 智能体名称
     */
    private String appName;

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

    /**
     * 智能体执行对象
     */
    private InMemoryRunner runner;
}
