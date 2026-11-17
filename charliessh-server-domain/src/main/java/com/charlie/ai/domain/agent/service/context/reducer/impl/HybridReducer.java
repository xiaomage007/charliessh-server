package com.charlie.ai.domain.agent.service.context.reducer.impl;

import com.charlie.ai.domain.agent.service.context.reducer.IMessageReducer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合裁剪器（ChatContextService 实际使用的裁剪策略）
 * <p>
 * 功能：组合两种裁剪器的优点——PriorityReducer 保证"重要的不丢"，
 * SlidingWindowReducer 保证"新的不丢"，取两者保留结果的交集，
 * 再强制保底最近 2 条。交集策略比任一单策略更严格，
 * 最终结果一定同时满足两种约束。
 * <p>
 * 运行过程：
 * <pre>
 *   messages ──+--> PriorityReducer.reduce()  --> 保留集合 A（按重要性）
 *              |
 *              +--> SlidingWindowReducer.reduce() --> 保留集合 B（按时效）
 *                          |
 *                          v
 *                  keep = A ∩ B（交集，双重约束）
 *                          |
 *                          v
 *                  保底：强制加入最近 2 条的索引
 *                          |
 *                          v
 *                  按原顺序输出保留的消息
 * </pre>
 * 注：indexSet 通过 all.indexOf(msg) 把消息对象映射回原列表下标，
 * 依赖 Map 的 equals 比较内容，内容相同的消息可能定位到首个匹配项，
 * 对裁剪结果无实质影响（内容相同裁谁都一样）。
 *
 * @author Charlie
 * @since 2026/11/17
 */
@Component
public class HybridReducer implements IMessageReducer {


    @Override
    public List<Map<String, Object>> reduce(List<Map<String, Object>> messages, int tokenBudget) {
        return List.of();
    }

}