package com.charlie.ai.domain.agent.model.entity;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 装配命令
 * @author: Charlie
 * @date: 2026/10/14 9:35
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArmoryCommandEntity {

    private AiAgentConfigTableVO aiAgentConfigTableVO;

}
