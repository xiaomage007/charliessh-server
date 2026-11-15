package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;
import com.charlie.ai.domain.ssh.service.security.rule.TextRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则汇总入口
 * <p>
 * 规则按风险分类分散在各个 {@code XxxDangerRules} 中，本类负责把它们聚合成
 * 两张清单供检测器遍历：
 * <ul>
 *     <li>{@link #textRules()} —— 文本型规则，在完整命令文本上匹配；</li>
 *     <li>{@link #commandRules()} —— 命令型规则，在每条子命令上匹配。</li>
 * </ul>
 * 新增规则时只需在对应分类类里加一条，无需改动检测器。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class DangerRuleRegistry {

    /** 文本型规则清单 */
    private static final List<TextRule> TEXT_RULES = toTextRules(TextDangerRules.rules());

    /** 命令型规则清单 */
    private static final List<CommandRule> COMMAND_RULES = toCommandRules();

    private DangerRuleRegistry() {
    }

    /**
     * 文本型规则
     */
    public static List<TextRule> textRules() {
        return TEXT_RULES;
    }

    /**
     * 命令型规则
     */
    public static List<CommandRule> commandRules() {
        return COMMAND_RULES;
    }

    /**
     * 全部规则
     */
    public static List<DangerRule> all() {
        List<DangerRule> all = new ArrayList<>(TEXT_RULES.size() + COMMAND_RULES.size());
        all.addAll(TEXT_RULES);
        all.addAll(COMMAND_RULES);
        return Collections.unmodifiableList(all);
    }

    /**
     * 汇总各分类的命令型规则
     */
    private static List<CommandRule> toCommandRules() {
        List<CommandRule> rules = new ArrayList<>();
        addAll(rules, FileDangerRules.rules());
        addAll(rules, SystemDangerRules.rules());
        addAll(rules, PrivilegeDangerRules.rules());
        addAll(rules, DataDangerRules.rules());
        addAll(rules, NetworkDangerRules.rules());
        addAll(rules, AuditDangerRules.rules());
        addAll(rules, ContainerDangerRules.rules());
        return Collections.unmodifiableList(rules);
    }

    @SuppressWarnings("unchecked")
    private static List<TextRule> toTextRules(List<DangerRule> rules) {
        List<TextRule> textRules = new ArrayList<>(rules.size());
        for (DangerRule rule : rules) {
            textRules.add((TextRule) rule);
        }
        return Collections.unmodifiableList(textRules);
    }

    @SuppressWarnings("unchecked")
    private static void addAll(List<CommandRule> target, List<DangerRule> rules) {
        for (DangerRule rule : rules) {
            target.add((CommandRule) rule);
        }
    }

}
