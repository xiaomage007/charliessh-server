package com.charlie.ai.domain.agent.service.context.reducer.impl;

import com.charlie.ai.domain.agent.service.context.reducer.IMessageReducer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 滑动窗口裁剪器
 * <p>
 * 功能：保留"最近"的消息——从新到旧逐条装入窗口，
 * 受"窗口条数（20）+ token 预算"双重限制，任一超限即停止。
 * 时效性保障：最近的对话对当前推理最重要。
 * <p>
 * 运行过程：
 * <pre>
 *   messages:  [m0][m1][m2] ... [mN-2][mN-1]   （时间正序，右新左旧）
 *                                   <--------+
 *                                   从尾部向前逐条扫描
 *                     |
 *                     v
 *   每条消息: window.size() >= 20 ?            --是--> 停止
 *             usedTokens + msgTokens > 预算 ?  --是--> 停止
 *                     |否
 *                     v
 *             window.add(0, msg)   从头部插入，保持正序
 *                     |
 *                     v
 *   返回 window（最近 <=20 条且总 token 不超预算的消息）
 * </pre>
 * token 估算：粗略按 content.length()/2（2 个字符约 1 token）。
 *
 * @author Charlie
 * @since 2026/11/17
 */
@Component
public class SlidingWindowReducer implements IMessageReducer {

    private static final int DEFAULT_WINDOW_SIZE = 20;

    @Override
    public List<Map<String, Object>> reduce(List<Map<String, Object>> messages, int tokenBudget) {
        List<Map<String, Object>> window = new ArrayList<>();
        int usedTokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            int msgTokens = estimateToken(message);
            if (window.size() >= DEFAULT_WINDOW_SIZE || usedTokens + msgTokens >= tokenBudget)
                break;
            window.add(0, message);
            usedTokens += msgTokens;
        }
        return List.of();
    }

    /**
     * 估算单条消息 token 数。
     *
     * <p>这里采用非常轻量的经验估算：content.length() / 2。
     * 目的不是得到绝对精确的 token 数，而是给裁剪算法一个“够用的体积感知”。
     *
     * <p>案例：
     * <pre>
     *   content 长度 = 200 字符
     *   估算 token = 100
     * </pre>
     *
     * @param message 单条消息
     * @return 估算 token 数
     */
    private int estimateToken(Map<String, Object> message) {
        String content = stringValue(message.get("content"));
        return content.length();
    }

    /**
     * 安全获取对象字符串值，避免 null 干扰判断逻辑。
     *
     * @param value 原始对象
     * @return 非 null 字符串
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 统计单个消息组的 token 消耗。
     *
     * <p>案例：
     * <pre>
     *   group = [
     *     assistant(tool_calls, content 长度 40),
     *     tool(result, content 长度 200)
     *   ]
     *
     *   groupTokens = 40/2 + 200/2 = 120
     * </pre>
     *
     * @param group 消息组
     * @return 该组总 token 数
     */
    private int estimateTokens(MessageGroup group) {
        return group.getMessages()
                    .stream()
                    .mapToInt(this::estimateToken)
                    .sum();
    }

    /**
     * 消息组。
     *
     * <p>滑动窗口阶段按组保留，保证上下文单元完整。
     */
    @Data
    @AllArgsConstructor
    private static class MessageGroup {
        /**
         * 组内消息，按原始顺序保存。
         */
        private List<Map<String, Object>> messages;
        /**
         * 该组在原始消息列表中的起始索引。
         */
        private int startIndex;
    }

}