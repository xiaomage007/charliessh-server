package com.charlie.ai.domain.agent.model.valobj.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @Description: 动态上下文容器
 * @Author: Charlie
 * @Date: 2026/11/15 12:33
 */
@Data
@Builder
public class PromptContextVO {

    /**
     * 服务器信息（预留）
     */
    private String serverInfo;
    /**
     * 操作系统信息，如 Linux 5.15.0 x86_64
     */
    private String osInfo;
    /**
     * 当前登录用户，如 ubuntu
     */
    private String currentUser;
    /**
     * 当前工作目录，如 /opt/app
     */
    private String currentDirectory;
    /**
     * 最近执行的命令
     */
    private List<String> recentCommands;
    /**
     * 关键事件（里程碑）
     */
    private List<MilestoneVO> milestoneVOS;

    /**
     * 工具执行摘要
     */
    private String toolResultSummary;

    /**
     * 当前任务描述（首条用户消息）
     */
    private String taskDescription;

    /**
     * 当前意图标签（由意图识别系统经 PromptService.buildEnrichedMessage(intentLabel) 注入）。
     * <p>
     * 值为 {@code IntentTypeEnumVO.name()}（如 "DIAGNOSE"），可为 null 表示未识别。
     * 由 {@code DynamicPromptBuilder} 渲染为消息前缀 "[用户意图] xxx"，让主模型感知意图但不强制路由。
     */
    private String intentLabel;

}