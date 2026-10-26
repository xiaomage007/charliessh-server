package com.charlie.ai.domain.agent.model.valobj.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/16 8:22
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public enum AgentTypeEnum {

    Loop("循环执行", "loop", "loopAgentNode"),
    Parallel("并行执行", "parallel", "parallelAgentNode"),
    Sequential("串行执行", "sequential", "sequentialAgentNode"),
    ;

    private String name;
    private String type;
    private String node;

    public static AgentTypeEnum formType(String type) {
        if (type == null) {
            return null;
        }

        for (AgentTypeEnum agentTypeEnum : AgentTypeEnum.values()) {
            if (agentTypeEnum.getType().equals(type)) {
                return agentTypeEnum;
            }
        }

        return null;
    }

}
