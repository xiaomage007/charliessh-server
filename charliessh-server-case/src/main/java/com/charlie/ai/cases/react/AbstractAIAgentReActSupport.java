package com.charlie.ai.cases.react;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.api.dto.ReActResultDTO;
import com.charlie.ai.cases.react.factory.DefaultReActFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @Description: ReAct 支撑类（抽象基类）
 *
 * <p>封装 ReAct 循环的通用能力：
 * - 上下文管理（DynamicContext）
 * - SSE 事件发射
 * - 工具调用结果解析
 * - 响应格式化
 *
 * <p>节点路由链：
 * RootNode → AiCallNode → ToolCallNode → (ToolResultNode) → [循环或完成]
 * @Author: Charlie
 * @Date: 2026/11/9 8:23
 */
@Slf4j
public abstract class AbstractAIAgentReActSupport extends AbstractMultiThreadStrategyRouter<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> {

    @Getter
    @Setter
    protected StrategyHandler<ChatRequestDTO, DefaultReActFactory.DynamicContext, ReActResultDTO> defaultStrategyHandler = StrategyHandler.DEFAULT;

    @Resource
    protected ApplicationContext applicationContext;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会话 → 终端会话 ID 映射
     */
    protected static final Map<String, String> sessionTerminalMapping = new ConcurrentHashMap<>();

    protected static final InheritableThreadLocal<String> currentTerminalSession = new InheritableThreadLocal<>();


    @Override
    protected void multiThread(ChatRequestDTO chatRequestDTO, DefaultReActFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 暂无异步预加载需求
    }

    /**
     * 通用的 Bean 获取
     *
     * @param beanName 名称
     * @return 对应的bean
     */
    @SuppressWarnings("unchecked")
    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    // ═══════════════════════════════════════════════════════════════
    //  上下文绑定（ThreadLocal 方式，兼容异步线程）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 设置当前线程的终端会话 ID
     */
    protected static void setCurrentTerminalSession(String terminalSessionId) {
        currentTerminalSession.set(terminalSessionId);
    }

    /**
     * 获取当前线程的终端会话 ID
     */
    protected static String getCurrentTerminalSession() {
        return currentTerminalSession.get();
    }

    /**
     * 清除当前线程的终端会话 ID
     */
    protected static void clearCurrentTerminalSession() {
        currentTerminalSession.remove();
    }

    /**
     * 绑定会话与终端会话
     */
    protected static void bindTerminalSession(String sessionId, String terminalSessionId) {
        if (sessionId != null && terminalSessionId != null) {
            sessionTerminalMapping.put(sessionId, terminalSessionId);
        }
    }

    /**
     * 获取会话绑定的终端会话 ID
     */
    protected static String getTerminalSession(String sessionId) {
        return sessionId != null ? sessionTerminalMapping.get(sessionId) : null;
    }

    /**
     * 解绑会话与终端会话
     */
    protected static void unbindTerminalSession(String sessionId) {
        if (sessionId != null) {
            sessionTerminalMapping.remove(sessionId);
        }
    }



}