package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Description: AI 调用节点（ReAct 循环核心）
 *
 * <p>职责：
 * 1. 调用 ADK runner.runAsync() 获取事件流
 * 2. 处理文本内容，发送 SSE 事件
 * 3. 从 event.actions().stateDelta() 检测工具执行结果
 * 4. 如果有工具调用：存储到上下文，发送 SSE 事件，路由到 ToolCallNode
 * 5. 如果无工具调用：路由到 LoopDecisionNode
 *
 * <p>核心要点：
 * SpringAI 的 ChatModel.call() 自动执行工具，导致 event.functionCalls() 永远为空。
 * 修复方案：从 event.actions().stateDelta() 检测工具执行结果。
 * stateDelta 包含工具输出（key = output-key, value = 执行结果）。
 *
 * <p>ReAct 循环流程：
 * <pre>
 * RootNode
 *   └→ AiCallNode（调用 ADK runner，解析事件）
 *         ├→ [stateDelta 有结果] ToolCallNode → AiCallNode（循环）
 *         └→ [无工具调用] LoopDecisionNode → UserFeedbackNode
 * </pre>
 * @Author: Charlie
 * @Date: 2026/11/10 7:36
 */
@Slf4j
@Component("reactAiCallNode")
public class AiCallNode extends AbstractAIAgentReActSupport {



    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}