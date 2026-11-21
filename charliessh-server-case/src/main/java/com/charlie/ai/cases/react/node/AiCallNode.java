package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.IChatContextService;
import com.charlie.ai.domain.agent.service.IPromptService;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.*;

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

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Resource
    private IPromptService promptService;

    @Resource
    private IChatContextService chatContextService;

    /**
     * tool name 映射：stateDelta key -> tool name
     */
    private static final Map<String, String> STATE_DELTA_TOOL_MAPPING = Map.of("ssh_result",
            "executeCommand");


    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter,
            DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        log.info("ReAct AiCallNode - 开始 AI 调用，第 {} 步", dynamicContext.getStep() + 1);
        String agentId = dynamicContext.getAgentId();

        // 1. 获取 Agent 注册信息和 ADK Runner
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if (aiAgentRegisterVO == null) {
            throw new RuntimeException("Agent not found: " + agentId);
        }

        Runner runner = aiAgentRegisterVO.getRunner();

        // 2. 获取最新用户消息
        String lastUserMessage = getLastUserMessage(requestParameter, dynamicContext);

        // 3. 重置当前轮次缓冲
        dynamicContext.resetRoundBuffers();
        dynamicContext.resetRoundToolCalls();

        // 4. 裁剪消息历史（优先级 + 滑动窗口混合策略，8000 token 预算） - 这部分也可以作为配置，根据模型不同来调整。
        List<Map<String, Object>> trimmedHistory = chatContextService.trimHistory(
                dynamicContext.getMessageHistory(), 8000);
        dynamicContext.setMessageHistory(new ArrayList<>(trimmedHistory));

        // 5. 绑定终端会话 ID (使用线程和会话双重绑定)
        String terminalSessionId = dynamicContext.getTerminalSessionId();
        if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
            SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
        } else {
            terminalSessionId = getTerminalSession(dynamicContext.getSessionId());
            if (terminalSessionId != null) {
                SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
            }
        }

        // 6. 构建动态上下文并注入用户消息
        String enrichedMessage = buildEnrichedMessage(lastUserMessage, dynamicContext);
        log.debug("注入动态上下文后消息长度: {} -> {}", lastUserMessage.length(),
                enrichedMessage.length());

        // 7. 构建用户消息
        Content userContent = Content.builder()
                                     .role("user")
                                     .parts(Part.builder()
                                                .text(enrichedMessage)
                                                .build())
                                     .build();

        // 8. 重置 ReAct 循环标志
        dynamicContext.setStopReason(null);
        dynamicContext.setErrorMessage(null);

        // 9. 调用 ADK Runner 并处理事件流
        ResponseBodyEmitter emitter = dynamicContext.getEmitter();
        StringBuilder textAccumulator = new StringBuilder();
        boolean hasError = false;
        StringBuilder errorBuilder = new StringBuilder();

        log.info("调用 ADK Runner，用户消息: {}",
                lastUserMessage.length() > 200 ? lastUserMessage.substring(0,
                        200) + "..." : lastUserMessage);

        try {
            // ADK Runner 会自动执行工具（SpringAI ChatModel.call() 内部执行）
            // 事件流中 event.functionCalls() 为空，但 event.actions().stateDelta() 包含工具结果
            Iterator<Event> events = runner.runAsync(dynamicContext.getUserId(),
                                                   dynamicContext.getSessionId(),
                                                   userContent,
                                                   RunConfig.builder()
                                                            .build())
                                           .blockingIterable()
                                           .iterator();
            int eventCount = 0;
            while (events.hasNext()) {
                Event event = events.next();
                eventCount++;
                // 9.1 处理文本内容（模型的响应文本，包括工具调用后的总结）
                String eventText = event.stringifyContent();
                if (!eventText.isBlank()) {
                    textAccumulator.append(eventText);
                    dynamicContext.setAssistantContent(textAccumulator);
                    sendTextEvent(emitter, eventText, textAccumulator.toString());
                }

                // 9.2 从 stateDelta 检测工具执行结果
                EventActions actions = event.actions();
                if (actions != null) {
                    Map<String, Object> stateDelta = actions.stateDelta();
                    if (stateDelta != null && !stateDelta.isEmpty()) {
                        log.info("检测到 stateDelta 变更: keys={}", stateDelta.keySet());
                        for (Map.Entry<String, Object> entry : stateDelta.entrySet()) {
                            String stateKey = entry.getKey();
                            Object stateValue = entry.getValue();
                            // 跳过内部状态键（如 "REMOVED"）
                            if ("REMOVED".equals(stateValue)) {
                                continue;
                            }
                            String toolName = resolveToolName(stateKey);
                            String resultContent = formatStateValue(stateValue);
                            String toolCallId = "call_" + stateKey + "_" + System.currentTimeMillis();

                            log.info("工具执行结果: stateKey={}, toolName={}, result_length={}",
                                    stateKey, toolName, resultContent.length());

                            // 存储工具调用信息
                            HashMap<String, Object> toolCallInfo = new HashMap<>();
                            toolCallInfo.put("id", toolCallId);
                            toolCallInfo.put("name", toolName);
                            toolCallInfo.put("args", "");
                            dynamicContext.getCurrentToolCalls()
                                          .add(toolCallInfo);

                            // 存储工具结果
                            Map<String, Object> toolResultInfo = new HashMap<>();
                            toolResultInfo.put("id", toolCallId);
                            toolResultInfo.put("name", toolName);
                            toolResultInfo.put("content", resultContent);
                            toolResultInfo.put("status", "success");
                            dynamicContext.getCurrentToolResults()
                                          .add(toolResultInfo);
                            dynamicContext.getExecutedToolCalls()
                                          .add(toolCallInfo); // 汇总给前端

                            // 发送 SSE 工具调用事件
                            sendToolCallEvent(emitter, toolCallId, toolName, "executing");

                            // 发送 SSE 工具结果事件
                            sendToolResultEvent(emitter, toolCallId, resultContent, "success");

                            dynamicContext.incrementTotalToolCalls();
                            dynamicContext.incrementRoundToolCalls();

                            // 记录执行的命令到上下文 (优先记录 command 参数，如果 args 为空，暂时回退到结果摘要)
                            if ("executeCommand".equals(toolName)) {
                                String cmd = (String) toolCallInfo.get("args");
                                if (cmd != null && !cmd.isEmpty()) {
                                    recordExecutedCommand(dynamicContext, cmd);
                                } else {
                                    recordExecutedCommand(dynamicContext, "Executed " + toolName);
                                }
                            }

                            // 记录里程碑（工具结果）
                            promptService.detectAndRecordMilestone(
                                    dynamicContext.getSessionId(), "tool", resultContent);

                            // 记录到上下文提供者中（生成工具执行摘要，供下一轮 Prompt 注入）
                            chatContextService.pushToolResult(dynamicContext.getSessionId(),
                                    toolName, resultContent);

                        }
                    }
                }

                // 9.3 记录 assistant 内容到消息历史
                if (event.content()
                         .isPresent()) {
                    Content content = event.content()
                                           .get();
                    String role = content.role()
                                         .orElse("assistant");
                    if ("assistant".equals(role)) {
                        String text = event.stringifyContent();
                        if (!text.isBlank()) {
                            dynamicContext.appendAssistantMessage(text);
                        }
                    }
                }
            }

            log.info("ADK Runner 事件流处理完成，共 {} 个事件", eventCount);

        } catch (Exception e) {
            log.error("ADK Runner 调用失败", e);
            hasError = true;
            errorBuilder.append("ADK Runner error: ")
                        .append(e.getMessage());
            dynamicContext.setErrorMessage(errorBuilder.toString());
            dynamicContext.setStopReason("error");
        } finally {
            // 清除终端会话绑定
            SshExecuteAdkTool.clearCurrentTerminalSession();
        }

        // 10. 更新步数统计
        dynamicContext.incrementStep();
        dynamicContext.getResult()
                      .setTotalSteps(dynamicContext.getStep());

        log.info("ReAct AiCallNode - 第 {} 步完成，本轮工具调用 {} 次，文本长度 {}",
                dynamicContext.getStep(), dynamicContext.getRoundToolCallCount()
                                                        .get(), textAccumulator.length());

        // 11. 发送本轮结束事件
        sendRoundEndEvent(
                dynamicContext.getEmitter(),
                dynamicContext.getStep(),
                dynamicContext.getMaxSteps(),
                !hasError,
                dynamicContext.getTotalToolCallCount()
                              .get()
        );

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(
            ChatRequestDTO requestParameter,
            DefaultReActFactory.DynamicContext dynamicContext) throws Exception {

        List<Map<String, Object>> toolCalls = dynamicContext.getCurrentToolCalls();

        // 明确当前架构：ADK 自动执行主导。如果有工具调用，进入 ToolCallNode 主要是做日志和事件补偿。
        if (toolCalls != null && !toolCalls.isEmpty()) {
            log.info("本轮发现工具调用，路由到 ToolCallNode 处理结果事件");
            return getBean("reactToolCallNode");
        }

        log.info("本轮无工具调用，路由到 LoopDecisionNode");
        return getBean("reactLoopDecisionNode");
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取最新的一条用户消息
     */
    private String getLastUserMessage(ChatRequestDTO requestParameter,
            DefaultReActFactory.DynamicContext dynamicContext) {
        // 第一轮使用请求参数中的消息
        if (dynamicContext.getStep() == 0) {
            return requestParameter.getMessage();
        }

        // 后续轮次从历史记录中获取最后一条 user 消息
        List<Map<String, Object>> history = dynamicContext.getMessageHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = history.get(i);
            if ("user".equals(msg.get("role"))) {
                return (String) msg.get("content");
            }
        }

        return requestParameter.getMessage();
    }

    /**
     * 根据 stateKey 解析工具名称
     */
    private String resolveToolName(String stateKey) {
        if (STATE_DELTA_TOOL_MAPPING.containsKey(stateKey)) {
            return STATE_DELTA_TOOL_MAPPING.get(stateKey);
        }
        return stateKey.replace("_result", "");
    }

    /**
     * 格式化状态值为字符串
     */
    private String formatStateValue(Object stateValue) {
        if (stateValue == null) {
            return "";
        }
        if (stateValue instanceof String) {
            return (String) stateValue;
        }
        try {
            return objectMapper.writeValueAsString(stateValue);
        } catch (Exception e) {
            return stateValue.toString();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Phase 1: 动态上下文注入
    // ═══════════════════════════════════════════════════════════════

    /**
     * 记录执行的命令到最近命令列表 (修改为记录真实的 command 参数)
     */
    private void recordExecutedCommand(DefaultReActFactory.DynamicContext dynamicContext,
            String command) {
        if (command != null && !command.isBlank()) {
            dynamicContext.addRecentCommand(truncate(command, 200));
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 构建注入了动态上下文的用户消息
     * 委托 IPromptService 完成环境采集、里程碑获取、前缀构建
     */
    private String buildEnrichedMessage(String userMessage,
            DefaultReActFactory.DynamicContext dynamicContext) {
        // 记录用户消息的里程碑
        promptService.detectAndRecordMilestone(dynamicContext.getSessionId(), "user", userMessage);

        // 委托领域服务构建富化消息 (传入真实 userId)
        return promptService.buildEnrichedMessage(
                userMessage,
                dynamicContext.getSessionId(),
                dynamicContext.getUserId(),
                dynamicContext.getTerminalSessionId(),
                dynamicContext.getRecentCommands(),
                dynamicContext.getMessageHistory()
        );
    }
}