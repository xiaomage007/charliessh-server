package com.charlie.ai.domain.agent.service.armory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.IArmoryService;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/14 9:37
 */
@Slf4j
@Service
public class ArmoryService implements IArmoryService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Override
    public void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception {
        for (AiAgentConfigTableVO table : tables) {
            StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> handler = defaultArmoryFactory.armoryStrategyHandler();
            handler.apply(ArmoryCommandEntity.builder().aiAgentConfigTableVO(table).build(), new DefaultArmoryFactory.DynamicContext());
        }
    }

}
