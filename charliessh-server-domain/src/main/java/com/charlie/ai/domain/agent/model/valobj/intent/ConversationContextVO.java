package com.charlie.ai.domain.agent.model.valobj.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话级对话上下文（意图历史 + 任务态）
 *
 * <p>由 {@code ContextTracker} 按 sessionId 维护，生命周期内贯穿一次会话的所有轮次。
 * 既是分类器的<strong>上下文加权输入</strong>，也是反馈回路与 CONTINUE 的<strong>状态载体</strong>。
 *
 * <p>流转关系：
 * <pre>
 *   ContextTracker.getContext(sessionId)
 *        ↓ 提供
 *   ConversationContextVO ─┬─ recentIntents      → 规则/LLM 加权 +0.1
 *                          ├─ lastIntent          → 上下文连贯性参考
 *                          ├─ taskState           → CONTINUE 续接依据
 *                          └─ consecutiveFailures → ≥2 跳过规则层、强制 LLM 兜底
 *        ↑ 回写
 *   ContextTracker.updateContext / recordFeedback / setTaskState
 * </pre>
 *
 * <p>案例：
 * <pre>
 *   连续两轮识别为 DIAGNOSE，第三轮 "再看看"
 *     → recentIntents 含 DIAGNOSE → 规则加权 +0.1，倾向续 DIAGNOSE
 *   工具连续失败 2 次 → consecutiveFailures=2 → 跳规则层，全交主模型
 * </pre>
 *
 * @author Charlie
 * @since 2026/11/22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationContextVO {


}