package com.charlie.ai.domain.ssh.service.security.rule;

import com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel;

import java.util.regex.Pattern;

/**
 * 文本型规则：对<b>整条命令文本</b>做正则匹配
 * <p>
 * 只用于跨管道、跨命令的强信号（反弹 Shell、管道执行远程脚本、fork bomb 等）。
 * 这类构造一旦拆分到子命令层面就会丢失上下文（{@code curl x | bash} 的危险在于管道本身），
 * 因此必须在完整文本上匹配。
 * <p>
 * 收录标准：<b>误报率极低、召回价值高</b>。参数里出现关键字的命令不应由本类判定，
 * 那属于命令型规则的职责。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class TextRule extends DangerRule {

    /** 匹配模式 */
    private final Pattern pattern;

    public TextRule(String id, DangerRiskLevel level, String category, String reason, String regex) {
        super(id, level, category, reason);
        this.pattern = Pattern.compile(regex);
    }

    /**
     * 是否命中该规则
     *
     * @param fullText 归一化后的完整命令文本
     */
    public boolean matches(String fullText) {
        return fullText != null && pattern.matcher(fullText).find();
    }

}
