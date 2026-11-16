package com.charlie.ai.domain.agent.service;

/**
 * @Description: 上下文管理领域服务接口，负责 ReAct 对话的上下文聚合与裁剪
 *
 * <ul>
 *   <li>聚合各 ContextProvider 输出，组装 PromptContextVO</li>
 *   <li>在 token 预算内裁剪消息历史</li>
 *   <li>接收工具执行结果，供后续生成摘要</li>
 * </ul>
 * 在整体架构中的位置：
 * <pre>
 *   Case 层  AiCallNode / ToolCallNode
 *              |           ^
 *              v           | 富化消息 / 裁剪历史
 *   Domain 层  IChatContextService <-- PromptService（构建消息前缀）
 *              |
 *              +-- context/provider/impl/*Provider  （上下文采集：环境/任务/里程碑/工具摘要）
 *              +-- context/reducer/impl/*Reducer    （消息裁剪：优先级/滑动窗口/混合）
 * </pre>
 * @Author: Charlie
 * @Date: 2026/11/16 10:44
 */
public interface IChatContextService {

}