package com.charlie.ai.domain.agent.service.armory.matter.mcp.client.impl;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @ClassName: LocalToolMcpCreateService
 * @Description:
 * @Author: Charlie
 * @Date: 2026/10/18 15:56
 * @Version: 1.0
 */
@Slf4j
@Service
public class LocalToolMcpCreateService implements ToolMcpCreateService {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.LocalParameters local = toolMcp.getLocal();
        String name = local.getName();

        ToolCallbackProvider toolCallbackProvider = (ToolCallbackProvider) applicationContext.getBean(name);
        log.info("tool local mcp initialize {}", name);

        return toolCallbackProvider.getToolCallbacks();
    }
}