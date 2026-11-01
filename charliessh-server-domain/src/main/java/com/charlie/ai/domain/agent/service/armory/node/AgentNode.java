package com.charlie.ai.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.armory.AbstractArmorySupport;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.springai.SpringAI;
import com.google.adk.tools.FunctionTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName: AgentNode
 * @Description:
 * @Author: Charlie
 * @Date: 2026/10/15 21:59
 * @Version: 1.0
 */
@Slf4j
@Service
public class AgentNode extends AbstractArmorySupport {

    @Resource
    private AgentWorkflowNode agentWorkflowNode;

    @Resource
    private SshExecuteAdkTool sshExecuteAdkTool;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - AgentNode");

        ChatModel chatModel = dynamicContext.getChatModel();

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        List<AiAgentConfigTableVO.Module.Agent> agents = aiAgentConfigTableVO.getModule().getAgents();
        for (AiAgentConfigTableVO.Module.Agent agentConfig : agents) {
            LlmAgent.Builder builder = LlmAgent.builder().name(agentConfig.getName()).description(agentConfig.getDescription()).model(new SpringAI(chatModel)).instruction(agentConfig.getInstruction()).outputKey(agentConfig.getOutputKey());

            // 构建 ADK 工具列表 - 注意，这部分也可以提炼到配置文件
            List<Object> adkTools = new ArrayList<>();

            // 添加 SSH 执行工具（ADK 原生 FunctionTool）
            try {
                log.info("开始创建 SSH 执行工具, sshExecuteAdkTool={}", sshExecuteAdkTool);
                FunctionTool sshTool = FunctionTool.create(sshExecuteAdkTool, "executeCommand");
                log.info("FunctionTool 创建成功: name={}, declaration={}", sshTool.name(), sshTool.declaration().isPresent() ? sshTool.declaration().get() : "null");
                adkTools.add(sshTool);
                log.info("为 Agent [{}] 注册 SSH 执行工具成功", agentConfig.getName());
            } catch (Exception e) {
                log.error("创建 SSH ADK 工具失败", e);
            }

            // 注册工具到 Agent
            if (!adkTools.isEmpty()) {
                log.info("为 Agent [{}] 注册 {} 个工具", agentConfig.getName(), adkTools.size());
                builder.tools(adkTools);
            } else {
                log.warn("Agent [{}] 没有注册任何工具！", agentConfig.getName());
            }

            // 限制单轮对话内的最大步数（工具调用 + 模型推理的循环次数）
            // 没有这道闸门，一旦工具返回值不符合模型预期，模型会不断换着花样重试同一个目标，
            // 表现为「一直执行」且永不收敛。
            Integer maxSteps = aiAgentConfigTableVO.getModule().getChatModel().getMaxSteps();
            if (maxSteps != null && maxSteps > 0) {
                builder.maxSteps(maxSteps);
                log.info("为 Agent [{}] 设置 maxSteps={}", agentConfig.getName(), maxSteps);
            }

            LlmAgent llmAgent = builder.build();
            dynamicContext.getAgentGroup().put(agentConfig.getName(), llmAgent);

        }
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentWorkflowNode;
    }
}