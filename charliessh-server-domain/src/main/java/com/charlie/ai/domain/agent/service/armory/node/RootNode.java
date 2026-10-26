package com.charlie.ai.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.armory.AbstractArmorySupport;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @description: 根节点
 * @author: Charlie
 * @date: 2026/10/14 10:22
 */
@Slf4j
@Service
public class RootNode extends AbstractArmorySupport {

    @Resource
    private AiApiNode aiApiNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {

        // 路由到下一个节点
        return router(armoryCommandEntity, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        // 配置了下一个节点
        return aiApiNode;
    }
}
