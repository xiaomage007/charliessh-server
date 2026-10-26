package com.charlie.ai.domain.agent.service.armory.matter.mcp.client;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**
 * @ClassName: ToolMcpCreateService
 * @Description: 工具 MCP 构建服务
 * @Author: Charlie
 * @Date: 2026/10/18 15:41
 * @Version: 1.0
 */
public interface ToolMcpCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;

}