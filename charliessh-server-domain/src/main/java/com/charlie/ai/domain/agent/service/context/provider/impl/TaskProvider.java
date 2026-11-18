package com.charlie.ai.domain.agent.service.context.provider.impl;

import com.charlie.ai.domain.agent.service.context.provider.IContextProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 任务上下文提供者（order=20）
 *
 * <p>
 * 功能：从消息历史中提取首条 user 消息作为"当前任务描述"，
 * 让模型在长对话、多轮工具调用后仍能记住最初的目标（防"任务漂移"）。
 * <p>
 * 运行过程：
 * <pre>
 *   messageHistory（时间正序）
 *   +----------------------------------------------------+
 *   | [0] user:      "帮我排查 nginx 502"   <--+ 初始目标 |
 *   | [1] assistant: "好的，先看日志..."         |          |
 *   | [2] tool:      "tail -100 error.log..."  |          |
 *   | [3] assistant: "发现 upstream 超时..."   |          |
 *   | ...（几十轮后，模型容易忘记最初任务）       |          |
 *   +----------------------------------------------------+
 *                     |
 *                     v  TaskProvider.provide()
 *            从前往后找第一条 role=user 的消息
 *                     |
 *                     v
 *        Map{ taskDescription: "帮我排查 nginx 502" }
 *                     |
 *                     v
 *   DynamicPromptBuilder 渲染为消息前缀的 [当前任务] 段落
 *   --> 每轮对话都提醒模型"你最初的任务是什么"
 * </pre>
 * 设计说明：取"首条"而非"最近"——首条用户消息代表会话的初始目标；
 * 后续 user 消息多为补充/纠偏，已由 MilestoneProvider 覆盖。
 * @Author: Charlie
 * @Date: 2026/11/16 15:17
 */
@Order(20)
@Component
public class TaskProvider implements IContextProvider {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Map<String, Object> provide(String sessionId, String userId, String terminalSessionId,
                                       List<Map<String, Object>> messageHistory) {
        Map<String, Object> result = new HashMap<>();

        if (messageHistory != null) {
            messageHistory.stream()
                    .filter(m->"user".equals(m.get("role")))
                    .findFirst()
                    .ifPresent(m->result.put("taskDescription",m.get("content")));
        }

        return result;
    }

}