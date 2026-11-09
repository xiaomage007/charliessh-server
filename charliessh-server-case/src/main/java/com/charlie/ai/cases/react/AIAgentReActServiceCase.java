package com.charlie.ai.cases.react;

import com.charlie.ai.api.dto.ChatRequestDTO;
import com.charlie.ai.cases.IAIAgentReActServiceCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * @Description: AI 智能体 ReAct 执行服务实现
 *
 * <p>职责：
 * - 流式对话（SSE）：创建 emitter → 创建动态上下文 → 走节点链路
 * - 普通对话（非流式）：直接调用节点链路
 *
 * <p>节点链路：
 * RootNode → AiCallNode → LoopDecisionNode → UserFeedbackNode
 *
 * @Author: Charlie
 * @Date: 2026/11/9 8:07
 */
@Slf4j
@Service
public class AIAgentReActServiceCase implements IAIAgentReActServiceCase {

    @Override
    public String chat(ChatRequestDTO requestDTO) {
        return "";
    }

    @Override
    public ResponseBodyEmitter chatStream(ChatRequestDTO requestDTO) {
        return null;
    }

}