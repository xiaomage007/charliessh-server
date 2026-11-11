package com.charlie.ai.cases.react.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.AbstractAIAgentReActSupport;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.google.adk.runner.InMemoryRunner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

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

    /**
     * tool name 映射：stateDelta key -> tool name
     */
    private static final Map<String, String> STATE_DELTA_TOOL_MAPPING = Map.of("ssh_result", "executeCommand");


    @Override
    protected ReActResultDTO doApply(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        log.info("ReAct AiCallNode - 开始 AI 调用，第 {} 步", dynamicContext.getStep() + 1);
        String agentId = dynamicContext.getAgentId();

        // 1. 获取 Agent 注册信息和 ADK Runner
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
        if (aiAgentRegisterVO == null) {
            throw new RuntimeException("Agent not found: " + agentId);
        }

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        // 2. 获取最新用户消息
        String lastUserMessage = getLastUserMessage(requestParameter, dynamicContext);

        // 3. 重置当前轮次缓冲
        dynamicContext.resetRoundBuffers();

        // 4. 绑定终端会话 ID
        String terminalSessionId = dynamicContext.getTerminalSessionId();
        if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
            SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
        }

        // 5. 构建用户消息
        Content userContent = Content.builder().role("user").parts(Part.builder().text(lastUserMessage).build()).build();
        // 6. 重置 ReAct 循环标志
        dynamicContext.setStopReason(null);
        dynamicContext.setErrorMessage(null);

        // 7. 调用 ADK Runner 并处理事件流
        ResponseBodyEmitter emitter = dynamicContext.getEmitter();
        StringBuilder textAccumulator = new StringBuilder();
        int roundToolCalls = 0;
        boolean hasError = false;
        StringBuilder errorBuilder = new StringBuilder();

        log.info("调用 ADK Runner，用户消息: {}", lastUserMessage.length() > 200
                ? lastUserMessage.substring(0, 200) + "..." : lastUserMessage);



        return null;
    }

    @Override
    public StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> get(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取最新用户消息
     */
    private String getLastUserMessage(ChatRequestDTO requestParameter, DefaultReActFactory.DynamicContext dynamicContext) {
        if (requestParameter.getMessage() != null && !requestParameter.getMessage().isEmpty()) {
            return requestParameter.getMessage();
        }

        List<Map<String, Object>> history = dynamicContext.getMessageHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = history.get(i);
            if ("user".equals(msg.get("role"))) {
                return (String) msg.get("content");
            }
        }

        return "";
    }

    /**
     * 从 stateDelta key 解析工具名称
     */
    private String resolveToolName(String stateKey) {
        // 已知映射
        String mapped = STATE_DELTA_TOOL_MAPPING.get(stateKey);
        if (mapped != null) {
            return mapped;
        }

        // 从 key 推断：去掉 _result 后缀
        if (stateKey.endsWith("_result")) {
            return stateKey.substring(0, stateKey.length() - 7);
        }

        return stateKey;
    }

    /**
     * 格式化 stateDelta 值为字符串
     */
    private String formatStateValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}