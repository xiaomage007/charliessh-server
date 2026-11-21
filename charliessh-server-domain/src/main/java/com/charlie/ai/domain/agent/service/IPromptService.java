package com.charlie.ai.domain.agent.service;

import java.util.List;
import java.util.Map;

/**
 * @Description: 提示词领域服务接口
 *
 * <p>
 * 封装动态 Prompt 构建、里程碑追踪、环境信息采集等能力，
 * 使 case 层仅依赖此接口，不直接接触 DynamicPromptBuilder / MilestoneTracker 等内部组件。
 * @Author: Charlie
 * @Date: 2026/11/15 15:13
 */
public interface IPromptService {

    /**
     * 构建带动态上下文前缀的富化消息。
     *
     * @param userMessage       原始用户消息
     * @param sessionId         会话 ID
     * @param userId            用户 ID
     * @param terminalSessionId 终端会话 ID
     * @param recentCommands    最近执行命令
     * @param messageHistory    对话历史
     * @return 富化后的消息
     */
    String buildEnrichedMessage(String userMessage, String sessionId, String userId,
            String terminalSessionId, List<String> recentCommands,
            List<Map<String, Object>> messageHistory);

    /**
     * 检测并记录里程碑事件。
     *
     * @param sessionId 会话 ID
     * @param role      角色
     * @param content   内容
     */
    void detectAndRecordMilestone(String sessionId, String role, String content);

    /**
     * 清理指定会话的里程碑缓存。
     *
     * @param sessionId 会话 ID
     */
    void clearMilestones(String sessionId);

}