package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Description:
 * @Author: Charlie
 * @Date: 2026/11/10 7:42
 */
@Slf4j
@Component("loopDecisionNode")
public class LoopDecisionNode extends AbstractAIAgentReActSupport {
    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}