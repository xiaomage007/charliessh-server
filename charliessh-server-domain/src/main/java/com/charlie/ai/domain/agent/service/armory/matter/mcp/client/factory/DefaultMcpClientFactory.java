package com.charlie.ai.domain.agent.service.armory.matter.mcp.client.factory;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.charlie.ai.domain.agent.service.armory.matter.mcp.client.impl.LocalToolMcpCreateService;
import com.charlie.ai.domain.agent.service.armory.matter.mcp.client.impl.SSEToolMcpCreateService;
import com.charlie.ai.domain.agent.service.armory.matter.mcp.client.impl.StdioToolMcpCreateService;
import com.charlie.ai.types.enums.ResponseCode;
import com.charlie.ai.types.exception.AppException;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @ClassName: DefaultMcpClientFactory
 * @Description:
 * @Author: Charlie
 * @Date: 2026/10/18 16:02
 * @Version: 1.0
 */
@Slf4j
@Service
public class DefaultMcpClientFactory {

    @Resource
    private LocalToolMcpCreateService localToolMcpCreateService;
    @Resource
    private SSEToolMcpCreateService sseToolMcpCreateService;
    @Resource
    private StdioToolMcpCreateService stdioToolMcpCreateService;

    public ToolMcpCreateService getTooMcpCreateService(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp){
        if (null != toolMcp.getLocal()) return localToolMcpCreateService;
        if (null != toolMcp.getSse()) return sseToolMcpCreateService;
        if (null != toolMcp.getStdio()) return stdioToolMcpCreateService;
        throw new AppException(ResponseCode.NOT_FOUND_METHOD.getCode(), ResponseCode.NOT_FOUND_METHOD.getInfo());
    }

}