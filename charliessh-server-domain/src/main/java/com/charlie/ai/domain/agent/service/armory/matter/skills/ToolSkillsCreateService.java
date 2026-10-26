package com.charlie.ai.domain.agent.service.armory.matter.skills;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**
 * @description: 工具 skills 构建服务
 * @author: Charlie
 * @date: 2026/10/21 17:28
 */
public interface ToolSkillsCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception;

}
