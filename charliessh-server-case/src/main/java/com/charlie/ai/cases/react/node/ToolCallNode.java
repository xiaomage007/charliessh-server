package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: ReAct 工具执行节点
 *
 * <p>职责：
 * <br/>1. 从上下文中获取 AI 返回的工具调用列表（由 AiCallNode 设置）
 * <br/>2. 检查工具是否已被 ADK 自动执行（FunctionResponse 已存在）
 * <br/>3. 如果未执行，则手动执行工具
 * <br/>4. 将工具结果追加到消息历史
 * <br/>5. 发送 tool_call / tool_result SSE 事件
 * <br/>6. 路由：回到 AiCallNode 继续对话 或 到 LoopDecisionNode 完成
 *
 * <p>工具执行模式：
 * <ul>
 *   <li>ADK 自动执行模式：ADK runner.runAsync() 内部自动执行工具，
 *       ToolCallNode 仅处理已有结果并路由</li>
 *   <li>手动执行模式（未来扩展）：ToolCallNode 直接调用
 *       SshExecuteAdkTool.executeCommand() 等工具方法执行</li>
 * </ul>
 *
 * <p>ReAct 循环链路：
 * <pre>
 * RootNode
 *   └→ AiCallNode（调用模型，解析 FunctionCalls）
 *         └→ ToolCallNode（处理工具结果）
 *               └→ [有工具结果] 回到 AiCallNode 继续对话
 *               └→ [无工具调用] LoopDecisionNode
 *                     └→ UserFeedbackNode
 * </pre>
 * @Author: Charlie
 * @Date: 2026/11/10 7:38
 */
@Slf4j
@Component("reactToolCallNode")
public class ToolCallNode extends AbstractAIAgentReActSupport {


    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter,
                                     DefaultReActFactory.DynamicContext dynamicContext) throws Exception {


        // 路由
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(
            ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {

        List<Map<String, Object>> toolCalls = dynamicContext.getCurrentToolCalls();

        // 有工具调用（已执行完成）→ 回到 AiCallNode 继续对话
        if (toolCalls != null && !toolCalls.isEmpty()) {
            // 检查是否达到最大步数
            if (dynamicContext.getStep() >= dynamicContext.getMaxSteps()) {
                log.info("达到最大步数 {}，路由到 LoopDecisionNode", dynamicContext.getMaxSteps());
                dynamicContext.setStopReason("max_steps");
                return getBean("reactLoopDecisionNode");
            }

            log.info("工具调用处理完成，回到 AiCallNode 继续对话");
            return getBean("reactAiCallNode");
        }

        // 无工具调用 → 路由到 LoopDecisionNode
        log.info("无工具调用，路由到 LoopDecisionNode");
        return getBean("reactLoopDecisionNode");
    }

    // ═══════════════════════════════════════════════════════════════
    //  ADK 自动执行模式（基本上吧，不太会出问题，都能执行。除非模型很差的）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 处理 ADK 自动执行的工具结果
     * <p>ADK runner.runAsync() 内部已执行工具，FunctionResponse 已在事件流中返回
     */
    private void handleAdkToolResults(DefaultReActFactory.DynamicContext dynamicContext,
                                      List<Map<String, Object>> toolCalls,
                                      List<Map<String, Object>> toolResults) {

        // ADK runner 内部已管理对话历史（自动执行工具 + 追加 FunctionResponse）
        // 这里只做日志记录，不重复追加消息到 history
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        for (Map<String, Object> result : toolResults) {
            String id = (String) result.get("id");
            if (id != null) {
                resultMap.put(id, result);
            }
        }

        for (Map<String, Object> toolCall : toolCalls) {
            String toolCallId = (String) toolCall.get("id");
            String toolName = (String) toolCall.get("name");

            Map<String, Object> matchedResult = resultMap.get(toolCallId);
            if (matchedResult != null) {
                String content = (String) matchedResult.get("content");
                log.info("ADK 工具结果: id={}, name={}, result_length={}",
                         toolCallId, toolName, content != null ? content.length() : 0);
            } else {
                log.warn("未找到工具结果: id={}, name={}", toolCallId, toolName);
            }
        }

        // 清除本轮工具调用标记，避免重复路由
        dynamicContext.getCurrentToolCalls().clear();

    }


    // ═══════════════════════════════════════════════════════════════
    //  手动执行模式（未来扩展）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 手动执行工具调用
     * <p>当 ADK 未自动执行工具时，由 ToolCallNode 直接执行
     */
    private void handleManualToolExecution(DefaultReActFactory.DynamicContext dynamicContext,
                                           List<Map<String, Object>> toolCalls,
                                           ResponseBodyEmitter emitter) throws Exception {

    }




    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从 JSON 参数字符串中解析指定 key 的值
     */
    private String parseToolArg(String argsStr, String key) {
        if (argsStr == null || argsStr.isBlank()) return null;

        try {
            Map<String, Object> args = objectMapper.readValue(argsStr,
                                                              objectMapper.getTypeFactory()
                                                                          .constructMapType(Map.class, String.class,
                                                                                            Object.class));
            Object value = args.get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", e.getMessage());
            // 兜底：简单字符串匹配
            String pattern = "\"" + key + "\"";
            int idx = argsStr.indexOf(pattern);
            if (idx >= 0) {
                int colonIdx = argsStr.indexOf(":", idx + pattern.length());
                if (colonIdx >= 0) {
                    String remaining = argsStr.substring(colonIdx + 1).trim();
                    if (remaining.startsWith("\"")) {
                        int endQuote = remaining.indexOf("\"", 1);
                        if (endQuote > 0) {
                            return remaining.substring(1, endQuote);
                        }
                    }
                }
            }
            return null;
        }
    }

    /**
     * 截断过长的工具响应
     */
    private String truncateToolResponse(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "\n... (truncated, total " + content.length() + " chars)";
    }

}