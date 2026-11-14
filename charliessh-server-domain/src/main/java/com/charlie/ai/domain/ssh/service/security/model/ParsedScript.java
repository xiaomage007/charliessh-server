package com.charlie.ai.domain.ssh.service.security.model;

import java.util.List;

/**
 * 一整条待执行命令的解析结果
 * <p>
 * 与 {@link ParsedCommand} 的关系：
 * <ul>
 *     <li>{@link #getNormalized()} —— 归一化后的<b>完整</b>命令文本，
 *         文本型规则（跨管道、跨命令的强信号）需要在全文上匹配；</li>
 *     <li>{@link #getCommands()} —— 拆分后的<b>展平</b>命令列表，
 *         已包含 {@code bash -c} / {@code eval} / {@code $( )} 中的嵌套命令，
 *         命令型规则只需遍历这个列表即可，无需关心嵌套结构。</li>
 * </ul>
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class ParsedScript {

    /** 归一化后的完整命令文本 */
    private final String normalized;

    /** 展平后的命令列表（含嵌套命令） */
    private final List<ParsedCommand> commands;

    public ParsedScript(String normalized, List<ParsedCommand> commands) {
        this.normalized = normalized;
        this.commands = commands;
    }

    public String getNormalized() {
        return normalized;
    }

    public List<ParsedCommand> getCommands() {
        return commands;
    }

}
