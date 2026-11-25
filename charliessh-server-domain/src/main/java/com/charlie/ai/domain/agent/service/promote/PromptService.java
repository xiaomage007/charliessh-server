package com.charlie.ai.domain.agent.service.promote;

import com.charlie.ai.domain.agent.model.valobj.prompt.MilestoneVO;
import com.charlie.ai.domain.agent.model.valobj.prompt.PromptContextVO;
import com.charlie.ai.domain.agent.service.IChatContextService;
import com.charlie.ai.domain.agent.service.IPromptService;
import com.charlie.ai.domain.agent.service.promote.dynamic.DynamicPromptBuilder;
import com.charlie.ai.domain.agent.service.promote.dynamic.MilestoneTracker;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @Description: 提示词服务
 * <p>
 * 组合 DynamicPromptBuilder、MilestoneTracker、IChatContextService，
 * 向 case 层提供统一的提示词领域能力。
 * <p>
 * 上下文采集（环境/任务/里程碑/工具摘要）已下沉到 IChatContextService 的
 * Provider 体系，本类只负责组装前缀并拼接到用户消息。
 * @Author: Charlie
 * @Date: 2026/11/15 15:18
 */
@Slf4j
@Service
public class PromptService implements IPromptService {

    /**
     * 动态提示词构建器——负责组装结构化消息前缀（OS/用户/目录/里程碑/最近命令）
     */
    @Resource
    private DynamicPromptBuilder dynamicPromptBuilder;

    /**
     * 里程碑追踪器——检测并缓存用户纠偏、任务切换等关键事件，供动态 Prompt 引用
     */
    @Resource
    private MilestoneTracker milestoneTracker;

    /**
     * 上下文管理服务——聚合各 ContextProvider 输出，组装 PromptContextVO
     */
    @Resource
    private IChatContextService chatContextService;


    @Override
    public void detectAndRecordMilestone(String sessionId, String role, String content) {
        milestoneTracker.detectAndRecord(sessionId, role, content);
    }

    @Override
    public String buildEnrichedMessage(String userMessage, String sessionId, String userId, String terminalSessionId,
            List<String> recentCommands, List<Map<String, Object>> messageHistory, String intentLabel) {
        // 意图标签流转：intentLabel → PromptContextVO.intentLabel → DynamicPromptBuilder 前缀
        PromptContextVO promptContextVO = chatContextService.buildPromptContext(sessionId, userId, terminalSessionId,
                messageHistory);
        promptContextVO.setRecentCommands(recentCommands);
        promptContextVO.setIntentLabel(intentLabel);

        String prefix = dynamicPromptBuilder.buildMessagePrefix(promptContextVO);
        if (prefix.isEmpty()) {
            return userMessage;
        }

        return prefix + "\n---\n" + userMessage;
    }

    @Override
    public void clearMilestones(String sessionId) {
        milestoneTracker.clear(sessionId);
    }


}