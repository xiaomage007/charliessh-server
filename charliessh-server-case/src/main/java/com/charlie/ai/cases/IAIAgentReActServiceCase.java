package com.charlie.ai.cases;

import com.charlie.ai.api.dto.ChatRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * @Description: AI 智能体 ReAct 执行接口
 * <p>核心能力：
 * - 多轮对话循环（ReAct 模式）
 * - 工具调用执行（Tool Calling）
 * - 流式输出（SSE）
 * - 步数控制与终止条件判断
 *
 * <p>ReAct 循环流程：
 * 1. 构建用户消息，追加到历史
 * 2. 调用 AI（携带工具定义）
 * 3. 解析 AI 响应：
 *    - 包含 tool_calls → 执行工具 → 追加结果 → 回到步骤 1
 *    - 不包含 tool_calls → 返回结果给用户
 * 4. 终止条件：finish / 达到最大步数 / 异常
 * @Author: Charlie
 * @Date: 2026/11/8 8:12
 */
public interface IAIAgentReActServiceCase {

    String chat(ChatRequestDTO requestDTO);

    ResponseBodyEmitter chatStream(ChatRequestDTO requestDTO);

}