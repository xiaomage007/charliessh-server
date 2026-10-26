package com.charlie.ai.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.armory.AbstractArmorySupport;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.types.enums.ResponseCode;
import com.charlie.ai.types.exception.AppException;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.InMemoryRunner;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 执行节点
 * @author: Charlie
 * @date: 2026/10/16 14:55
 */
@Slf4j
@Service
public class RunnerNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - RunnerNode");

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        String appName = aiAgentConfigTableVO.getAppName();
        AiAgentConfigTableVO.Agent agent = aiAgentConfigTableVO.getAgent();
        String agentId = agent.getAgentId();
        String agentName = agent.getAgentName();
        String agentDesc = agent.getAgentDesc();

        InMemoryRunner runner = getRunner(dynamicContext, aiAgentConfigTableVO, appName);

        AiAgentRegisterVO aiAgentRegisterVO = AiAgentRegisterVO.builder()
                .appName(appName)
                .agentId(agentId)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .runner(runner)
                .build();

        // 注册到 Spring 容器
        registerBean(agentId, AiAgentRegisterVO.class, aiAgentRegisterVO);

        return aiAgentRegisterVO;
    }

    private InMemoryRunner getRunner(DefaultArmoryFactory.DynamicContext dynamicContext, AiAgentConfigTableVO aiAgentConfigTableVO, String appName) {
        AiAgentConfigTableVO.Module.Runner runnerConfig = aiAgentConfigTableVO.getModule().getRunner();

        // 装配发生在 ApplicationReadyEvent 阶段，任何一个配置表漏配 runner 都会让整个 Spring 上下文启动失败；
        // 所以这里必须自己把空值拦住并给出可读信息，而不是把裸 NPE 抛给 AiAgentAutoConfig 再包成 RuntimeException
        if (null == runnerConfig) {
            log.error("runner config is null. appName: {}", appName);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        String agentName = runnerConfig.getAgentName();
        if (StringUtils.isBlank(agentName)) {
            log.error("runner.agentName is blank. appName: {}", appName);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // runner.agentName 必须是前序节点已经构建并放入 agentGroup 的智能体名；
        // 名字写错时若不在装配期拦住，InMemoryRunner 会持有 null agent，
        // 直到 runAsync 时才在 InvocationContext.agent() 处抛空指针，现场离根因太远
        BaseAgent baseAgent = dynamicContext.getAgentGroup().get(agentName);
        if (null == baseAgent) {
            log.error("runner.agentName {} not found in agentGroup. appName: {}", agentName, appName);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 扩展插件
        List<BasePlugin> plugins;
        List<String> pluginNameList = runnerConfig.getPluginNameList();
        if (null != pluginNameList && !pluginNameList.isEmpty()) {
            plugins = new ArrayList<>();
            for (String pluginName : pluginNameList) {
                BasePlugin plugin = getBean(pluginName);
                plugins.add(plugin);
            }
        } else {
            plugins = ImmutableList.of();
        }


        return new InMemoryRunner(baseAgent, appName, plugins);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }
}
