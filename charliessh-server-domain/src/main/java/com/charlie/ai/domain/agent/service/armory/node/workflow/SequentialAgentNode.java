package com.charlie.ai.domain.agent.service.armory.node.workflow;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.armory.AbstractArmorySupport;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.domain.agent.service.armory.node.RunnerNode;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/16 9:27
 */
@Slf4j
@Service("sequentialAgentNode")
public class SequentialAgentNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - SequentialAgentNode");

        AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow = dynamicContext.getCurrentAgentWorkflow();
        List<String> subAgentNames = currentAgentWorkflow.getSubAgents();
        List<BaseAgent> subAgents = dynamicContext.queryAgentList(subAgentNames);

        SequentialAgent sequentialAgent =
                SequentialAgent.builder()
                        .name(currentAgentWorkflow.getName())
                        .description(currentAgentWorkflow.getDescription())
                        .subAgents(subAgents)
                        .build();

        dynamicContext.getAgentGroup().put(currentAgentWorkflow.getName(), sequentialAgent);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("agentWorkflowNode");
    }
}
