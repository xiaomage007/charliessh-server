package com.charlie.ai.domain.ssh.service.security.rule;

import com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel;
import com.charlie.ai.domain.ssh.service.security.model.ParsedCommand;

import java.util.function.Function;

/**
 * 危险命令规则基类
 * <p>
 * 一条规则 = <b>元信息</b>（编号、风险等级、风险分类、命中原因）
 * + <b>匹配逻辑</b>（由子类提供）。
 * <p>
 * 规则分为两类：
 * <ul>
 *     <li>{@link CommandRule} —— 针对<b>单条子命令</b>做语义匹配，
 *         例如"基础命令是 rm 且带 r/f 选项且目标是 /"；</li>
 *     <li>{@link TextRule} —— 针对<b>整条命令文本</b>做正则匹配，
 *         用于跨管道、跨命令的强信号（反弹 Shell、管道执行远程脚本等）。</li>
 * </ul>
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public abstract class DangerRule {

    /** 规则编号，用于审计与白名单放行 */
    private final String id;

    /** 风险等级 */
    private final DangerRiskLevel level;

    /** 风险分类 */
    private final String category;

    /** 命中原因；允许基于命中的命令生成动态描述 */
    private final Function<ParsedCommand, String> reasonFn;

    protected DangerRule(String id, DangerRiskLevel level, String category, String reason) {
        this(id, level, category, command -> reason);
    }

    protected DangerRule(String id, DangerRiskLevel level, String category,
                         Function<ParsedCommand, String> reasonFn) {
        this.id = id;
        this.level = level;
        this.category = category;
        this.reasonFn = reasonFn;
    }

    public String getId() {
        return id;
    }

    public DangerRiskLevel getLevel() {
        return level;
    }

    public String getCategory() {
        return category;
    }

    /**
     * 生成命中原因描述
     *
     * @param command 命中的命令；文本型规则传 null
     */
    public String reason(ParsedCommand command) {
        return reasonFn.apply(command);
    }

}
