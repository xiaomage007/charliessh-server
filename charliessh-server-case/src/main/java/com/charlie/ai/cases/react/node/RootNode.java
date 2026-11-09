package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ReAct Root Node（根节点）
 *
 * <p>职责：
 * 1. 从 ChatRequestDTO 提取会话参数
 * 2. 初始化 DynamicContext
 * 3. 绑定终端会话 ID（ThreadLocal）
 * 4. 路由到 AiCallNode
 *
 * <p>节点链：
 * RootNode → AiCallNode → ToolCallNode → LoopDecisionNode → UserFeedbackNode
 *
 * @author Charlie
 * @since 2026/11/9
 */
@Slf4j
@Component("reactRootNode")
public class RootNode extends AbstractAIAgentReActSupport {

    private static final int DEFAULT_MAX_STEPS = 50;
    private static final int DEFAULT_MAX_TOOL_CALLS = 200;
    private static final int DEFAULT_MAX_TOOL_CALLS_PER_ROUND = 10;

    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        log.info("ReAct RootNode - 初始化上下文");

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("AiCallNode");
    }
}
