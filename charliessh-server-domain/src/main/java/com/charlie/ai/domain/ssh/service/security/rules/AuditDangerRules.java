package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.model.ParsedCommand;
import com.charlie.ai.domain.ssh.service.security.policy.SensitivePathPolicy;
import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;
import java.util.Locale;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 审计痕迹与持久化类规则
 * <p>
 * 覆盖两类行为：
 * <ul>
 *     <li><b>反审计</b>：清除 Shell 历史、清空系统日志——目的是抹掉操作痕迹；</li>
 *     <li><b>持久化后门</b>：写入 authorized_keys、crontab、启动脚本、ld.so.preload
 *         ——目的是在清理之后仍然存活。</li>
 * </ul>
 * 这两类行为在正常运维中几乎不出现，因此误报率低、拦截价值高。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class AuditDangerRules {

    private AuditDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                clearHistory(),
                unsetHistoryFile(),
                exportHistorySize(),
                deleteHistoryFile(),
                writeHistoryFile(),
                vacuumJournal(),
                writeAuthorizedKeys(),
                modifyAuthorizedKeys(),
                persistenceScript(),
                writeCrontab()
        );
    }

    /**
     * history -c 清空 Shell 历史
     */
    private static CommandRule clearHistory() {
        return new CommandRule("AUDIT_HISTORY_CLEAR", HIGH, "审计与痕迹",
                cmd -> "清空 Shell 历史记录属于反审计行为: " + cmd.getRaw(),
                cmd -> cmd.is("history") && cmd.hasArg("-c"));
    }

    /**
     * unset HISTFILE 关闭历史记录
     */
    private static CommandRule unsetHistoryFile() {
        return new CommandRule("AUDIT_UNSET_HISTFILE", HIGH, "审计与痕迹",
                cmd -> "取消 HISTFILE 会关闭命令历史记录: " + cmd.getRaw(),
                cmd -> cmd.is("unset")
                        && (cmd.hasArg("HISTFILE") || cmd.hasArg("HISTFILESIZE")));
    }

    /**
     * export HISTSIZE=0 把历史长度设为 0
     */
    private static CommandRule exportHistorySize() {
        return new CommandRule("AUDIT_EXPORT_HISTSIZE", HIGH, "审计与痕迹",
                cmd -> "把历史记录长度设为 0 属于反审计行为: " + cmd.getRaw(),
                cmd -> cmd.is("export")
                        && (lower(cmd).contains("histsize=0") || lower(cmd).contains("histfilesize=0")));
    }

    /**
     * 删除 Shell 历史文件
     */
    private static CommandRule deleteHistoryFile() {
        return new CommandRule("AUDIT_HISTORY_FILE", HIGH, "审计与痕迹",
                cmd -> "操作 Shell 历史文件属于反审计行为: " + cmd.getRaw(),
                cmd -> cmd.getOperands().stream().anyMatch(AuditDangerRules::isHistoryFile));
    }

    /**
     * 写入 Shell 历史文件
     */
    private static CommandRule writeHistoryFile() {
        return new CommandRule("AUDIT_HISTORY_WRITE", HIGH, "审计与痕迹",
                cmd -> "写入 Shell 历史文件属于反审计行为: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream().anyMatch(AuditDangerRules::isHistoryFile));
    }

    /**
     * 压缩 / 清空 systemd 日志
     */
    private static CommandRule vacuumJournal() {
        return new CommandRule("AUDIT_JOURNAL_VACUUM", MEDIUM, "审计与痕迹",
                cmd -> "清空系统日志会破坏审计线索: " + cmd.getRaw(),
                cmd -> cmd.is("journalctl")
                        && (cmd.hasArg("--vacuum-time=1s") || cmd.hasArg("--vacuum-size=1K")));
    }

    /**
     * 向 authorized_keys 追加内容 —— 等同于植入 SSH 后门
     */
    private static CommandRule writeAuthorizedKeys() {
        return new CommandRule("AUDIT_AUTHORIZED_KEYS", HIGH, "持久化后门",
                cmd -> "向 authorized_keys 写入内容等同于植入 SSH 后门: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream().anyMatch(t -> t.contains("authorized_keys")));
    }

    /**
     * 用 tee / cp / mv 等方式修改 authorized_keys
     */
    private static CommandRule modifyAuthorizedKeys() {
        return new CommandRule("AUDIT_AUTHORIZED_KEYS_WRITE", HIGH, "持久化后门",
                cmd -> "修改 authorized_keys 等同于植入 SSH 后门: " + cmd.getRaw(),
                cmd -> cmd.is("tee", "cp", "mv", "install", "sed")
                        && cmd.getOperands().stream().anyMatch(o -> o.contains("authorized_keys")));
    }

    /**
     * 写入启动脚本 / 定时任务目录 / ld.so.preload
     */
    private static CommandRule persistenceScript() {
        return new CommandRule("AUDIT_PERSISTENCE", HIGH, "持久化后门",
                cmd -> "写入系统启动/定时任务文件属于持久化行为: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream().anyMatch(AuditDangerRules::isPersistenceTarget));
    }

    /**
     * 直接写入 crontab
     */
    private static CommandRule writeCrontab() {
        return new CommandRule("AUDIT_CRONTAB_WRITE", HIGH, "持久化后门",
                cmd -> "直接写入 crontab 属于持久化行为: " + cmd.getRaw(),
                cmd -> cmd.is("crontab") && cmd.hasArg("-"));
    }

    // ---------- 内部辅助 ----------

    private static boolean isHistoryFile(String target) {
        return target.contains(".bash_history")
                || target.contains(".zsh_history")
                || target.contains(".sh_history");
    }

    private static boolean isPersistenceTarget(String target) {
        String path = SensitivePathPolicy.normalizePath(target);
        return path.startsWith("/etc/rc.local")
                || path.startsWith("/etc/profile.d/")
                || path.equals("/etc/ld.so.preload")
                || path.startsWith("/etc/cron")
                || path.contains(".bashrc");
    }

    private static String lower(ParsedCommand cmd) {
        return cmd.getRaw().toLowerCase(Locale.ROOT);
    }

}
