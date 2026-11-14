package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;

/**
 * 数据与数据库类规则
 * <p>
 * 覆盖：DROP / TRUNCATE / 无条件 DELETE、Redis flush、删库命令。
 * <p>
 * 这一类的判定只能基于<b>命令文本</b>（SQL 关键字在参数里，不在基础命令名上），
 * 因此统一用整条子命令的小写文本做正则匹配。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class DataDangerRules {

    /** DROP 操作 */
    private static final Pattern DROP_PATTERN =
            Pattern.compile("\\bdrop\\s+(database|schema|table)\\b");

    /** TRUNCATE 操作 */
    private static final Pattern TRUNCATE_PATTERN =
            Pattern.compile("\\btruncate\\s+(table\\s+)?[\\w`.]+");

    /** DELETE 操作 */
    private static final Pattern DELETE_PATTERN = Pattern.compile("\\bdelete\\s+from\\b");

    private DataDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                dropObject(),
                truncateTable(),
                deleteWithoutWhere(),
                redisFlush(),
                dropDatabase()
        );
    }

    /**
     * DROP DATABASE / SCHEMA / TABLE
     */
    private static CommandRule dropObject() {
        return new CommandRule("DATA_DROP", HIGH, "数据与数据库",
                cmd -> "执行了 DROP 操作，会永久删除库表结构与数据: " + cmd.getRaw(),
                cmd -> DROP_PATTERN.matcher(lower(cmd)).find());
    }

    /**
     * TRUNCATE TABLE
     */
    private static CommandRule truncateTable() {
        return new CommandRule("DATA_TRUNCATE", HIGH, "数据与数据库",
                cmd -> "执行了 TRUNCATE，会清空表中全部数据: " + cmd.getRaw(),
                cmd -> TRUNCATE_PATTERN.matcher(lower(cmd)).find());
    }

    /**
     * 不带 where 条件的 DELETE
     */
    private static CommandRule deleteWithoutWhere() {
        return new CommandRule("DATA_DELETE_ALL", HIGH, "数据与数据库",
                cmd -> "不带 where 条件的 DELETE 会删除表中全部数据: " + cmd.getRaw(),
                cmd -> DELETE_PATTERN.matcher(lower(cmd)).find() && !lower(cmd).contains(" where "));
    }

    /**
     * Redis flushall / flushdb
     */
    private static CommandRule redisFlush() {
        return new CommandRule("DATA_REDIS_FLUSH", HIGH, "数据与数据库",
                cmd -> "Redis flushall/flushdb 会清空数据库: " + cmd.getRaw(),
                cmd -> cmd.is("flushall", "flushdb")
                        || lower(cmd).contains("flushall")
                        || lower(cmd).contains("flushdb"));
    }

    /**
     * 删库命令
     */
    private static CommandRule dropDatabase() {
        return new CommandRule("DATA_DROP_DB", HIGH, "数据与数据库",
                cmd -> "删除数据库: " + cmd.getRaw(),
                cmd -> cmd.is("dropdb") || lower(cmd).contains("db.dropdatabase"));
    }

    private static String lower(com.charlie.ai.domain.ssh.service.security.model.ParsedCommand cmd) {
        return cmd.getRaw().toLowerCase(Locale.ROOT);
    }

}
