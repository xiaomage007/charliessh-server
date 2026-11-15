package com.charlie.ai.domain.ssh.service.security.rule;

import com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel;
import com.charlie.ai.domain.ssh.model.entity.ParsedCommand;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 命令型规则：对<b>单条子命令</b>做语义匹配
 * <p>
 * 匹配逻辑以 {@link Predicate} 表达，因此规则定义处可以像写断言一样直观：
 * <pre>{@code
 * CommandRule.of("FILE_RM_ROOT", FORBIDDEN, "文件破坏",
 *         cmd -> cmd.is("rm") && cmd.hasShortFlag('r') && isRootTarget(cmd.firstOperand()))
 * }</pre>
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class CommandRule extends DangerRule {

    /** 匹配谓词 */
    private final Predicate<ParsedCommand> matcher;

    public CommandRule(String id, DangerRiskLevel level, String category, String reason,
                       Predicate<ParsedCommand> matcher) {
        super(id, level, category, reason);
        this.matcher = matcher;
    }

    public CommandRule(String id, DangerRiskLevel level, String category,
                       Function<ParsedCommand, String> reasonFn, Predicate<ParsedCommand> matcher) {
        super(id, level, category, reasonFn);
        this.matcher = matcher;
    }

    /**
     * 是否命中该规则
     */
    public boolean matches(ParsedCommand command) {
        return command != null && matcher.test(command);
    }

}
