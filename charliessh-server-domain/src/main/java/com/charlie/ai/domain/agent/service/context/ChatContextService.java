package com.charlie.ai.domain.agent.service.context;

import com.charlie.ai.domain.agent.model.valobj.prompt.MilestoneVO;
import com.charlie.ai.domain.agent.model.valobj.prompt.PromptContextVO;
import com.charlie.ai.domain.agent.service.IChatContextService;
import com.charlie.ai.domain.agent.service.context.provider.IContextProvider;
import com.charlie.ai.domain.agent.service.context.provider.impl.ToolResultProvider;
import com.charlie.ai.domain.agent.service.context.reducer.impl.HybridReducer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 上下文管理领域服务（context 包的聚合核心）
 * <p>
 * 功能：ReAct 对话的"上下文中枢"，对上（PromptService/Case层）提供三个能力：
 * <pre>
 *   buildPromptContext()  聚合所有 Provider 的输出 --> PromptContextVO
 *   trimHistory()         在 token 预算内裁剪消息历史（默认 8000）
 *   pushToolResult()      接收工具执行结果，供生成工具摘要
 * </pre>
 * 整体运行过程（一次 ReAct 循环中的调用时序）：
 * <pre>
 *   AiCallNode.doApply()
 *        |
 *        | (1) trimHistory(history, 8000)
 *        |        |
 *        |        v
 *        |     HybridReducer = PriorityReducer ∩ SlidingWindowReducer + 保底2条
 *        |        |
 *        |        v
 *        |     裁剪后的历史回写 DynamicContext
 *        |
 *        | (2) PromptService.buildEnrichedMessage(...)
 *        |        |
 *        |        v
 *        |     buildPromptContext(sessionId, userId, terminalSessionId, history)
 *        |        |
 *        |        +--> for provider in providers(按order排序, 跳过disabled):
 *        |        |        TerminalState(10)  {osInfo, currentUser, currentDirectory, uptime}
 *        |        |        Task(20)           {taskDescription}
 *        |        |        Milestone(30)      {milestoneVOS}
 *        |        |        ToolResult(40)     {toolResultSummary}
 *        |        |     finalCtx.putAll(...)   合并所有键值对
 *        |        |
 *        |        v
 *        |     PromptContextVO --> DynamicPromptBuilder --> 消息前缀
 *        |
 *        | (3) 工具执行后 pushToolResult(sessionId, toolName, result)
 *        |        |
 *        |        v
 *        |     ToolResultProvider.pushResult() 缓存 + 使摘要失效
 *        |     （下一轮 (2) 时新摘要进入 Prompt）
 * </pre>
 * 装配机制：@Resource List<ContextProvider> 由 Spring 按类型自动收集
 * 全部 Provider 实现，@PostConstruct 时按 getOrder() 排序——新增 Provider
 * 只需加 @Component，无需修改本类。
 * @Author: Charlie
 * @Date: 2026/11/16 10:50
 */
@Service
public class ChatContextService implements IChatContextService {

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 8000;

    private final List<IContextProvider> providers;

    @Resource
    private HybridReducer hybridReducer;

    @Resource
    private ToolResultProvider toolResultProvider;

    public ChatContextService(List<IContextProvider> providers) {
        this.providers = providers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PromptContextVO buildPromptContext(String sessionId, String userId,
            String terminalSessionId, List<Map<String, Object>> messageHistory) {

        Map<String, Object> finalCtx = new HashMap<>();

        for (IContextProvider provider : providers) {
            if (!provider.enabled()) continue;
            Map<String, Object> ctx = provider.provide(sessionId, userId, terminalSessionId,
                    messageHistory);
            if (ctx != null) {
                finalCtx.putAll(ctx);
            }
        }

        return PromptContextVO.builder()
                              .osInfo((String) finalCtx.get("osInfo"))
                              .currentUser((String) finalCtx.get("currentUser"))
                              .currentDirectory((String) finalCtx.get("currentDirectory"))
                              .serverInfo((String) finalCtx.get("serverInfo"))
                              .milestoneVOS((List<MilestoneVO>) finalCtx.get("milestoneVOS"))
                              .toolResultSummary((String) finalCtx.get("toolResultSummary"))
                              .taskDescription((String) finalCtx.get("taskDescription"))
                              .build();

    }

    @Override
    public List<Map<String, Object>> trimHistory(List<Map<String, Object>> history,
            int tokenBudget) {
        if (history == null || history.isEmpty()) return Collections.emptyList();

        return hybridReducer.reduce(history, tokenBudget > 0 ? tokenBudget :
                DEFAULT_MAX_CONTEXT_TOKENS);
    }

    @Override
    public void pushToolResult(String sessionId, String toolName, String result) {
        toolResultProvider.pushResult(sessionId, toolName, result);
    }

    /**
     * 它主要清理 ToolResultProvider 中按 session 缓存的工具结果和摘要。为什么需要清理？
     * 因为工具结果是会话级临时上下文，适合当前 ReAct 循环复用，不适合跨会话或跨任务残留。
     * 否则上一次“排查 nginx”的工具摘要，可能污染下一次“安装 docker”的任务
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void clearSessionContext(String sessionId) {
        if (sessionId != null) {
            toolResultProvider.clear(sessionId);
        }
    }

}