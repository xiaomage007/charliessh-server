package com.charlie.ai.cases.react.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description: ReAct 动态上下文
 *
 * <p>参考 DefaultArmoryFactory.DynamicContext，为 ReAct 执行链路提供状态存储：
 * - 会话信息（sessionId, userId, agentId）
 * - 消息历史（messages + toolResults）
 * - ReAct 循环状态（步数、工具调用计数）
 * - SSE 发射器
 * - 工具定义（ToolCallback[]）
 *
 * <p>ReAct 循环数据流：
 * <pre>
 * RootNode         → 初始化上下文，绑定 SSE emitter
 * AiCallNode       → 构建 AI 请求，追加用户消息到 history
 * ToolCallNode     → 解析 tool_calls，执行工具，追加结果到 history
 * LoopDecisionNode → 判断是否继续（max_steps / finish / 无工具调用）
 * UserFeedbackNode → 发送最终结果，完成 SSE
 * </pre>
 * @Author: Charlie
 * @Date: 2026/11/9 8:10
 */
public class DefaultReActFactory {

    /**
     * 动态上下文
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DynamicContext {


    }

}