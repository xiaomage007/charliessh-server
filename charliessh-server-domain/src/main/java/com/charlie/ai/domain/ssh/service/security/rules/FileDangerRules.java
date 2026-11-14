package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.model.ParsedCommand;
import com.charlie.ai.domain.ssh.service.security.policy.SensitivePathPolicy;
import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.FORBIDDEN;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 文件与磁盘破坏类规则
 * <p>
 * 覆盖：删除类（rm / find -delete / mv / cp）、格式化与写盘（mkfs / dd / wipefs）、
 * 权限与属性变更（chmod / chown / chattr / ln）。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class FileDangerRules {

    private FileDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                rmRootTarget(),
                rmCriticalPath(),
                rmSensitivePath(),
                rmRecursiveForce(),
                rmBulkFromPipe(),
                redirectToCriticalPath(),
                redirectToSystemPath(),
                ddWriteBlockDevice(),
                formatFilesystem(),
                wipefs(),
                shred(),
                partitionTool(),
                chmodWorldWritable(),
                chownSystemPath(),
                chattr(),
                lnOverwrite(),
                truncateFile(),
                findDelete(),
                findExecRm(),
                moveCriticalPath()
        );
    }

    // ---------- 删除类 ----------

    /**
     * 递归删除根目录 / 用户主目录：等同于摧毁整机
     */
    private static CommandRule rmRootTarget() {
        Predicate<String> isRoot = SensitivePathPolicy::isRootTarget;
        return new CommandRule("FILE_RM_ROOT", FORBIDDEN, "文件破坏",
                cmd -> "递归删除根目录或用户主目录: " + findRmOperand(cmd, isRoot),
                cmd -> cmd.is("rm") && isRecursive(cmd) && findRmOperand(cmd, isRoot) != null);
    }

    /**
     * 删除系统关键文件（/etc/passwd、/etc/sudoers 等）
     */
    private static CommandRule rmCriticalPath() {
        Predicate<String> isCritical = SensitivePathPolicy::isCriticalPath;
        return new CommandRule("FILE_RM_CRITICAL", HIGH, "文件破坏",
                cmd -> "删除系统关键文件或目录: " + findRmOperand(cmd, isCritical),
                cmd -> cmd.is("rm") && findRmOperand(cmd, isCritical) != null);
    }

    /**
     * 递归删除系统 / 业务关键目录下的内容
     */
    private static CommandRule rmSensitivePath() {
        Predicate<String> isSensitive = SensitivePathPolicy::isSensitivePath;
        return new CommandRule("FILE_RM_SYSTEM", HIGH, "文件破坏",
                cmd -> "递归删除系统/业务关键目录下的内容: " + findRmOperand(cmd, isSensitive),
                cmd -> cmd.is("rm") && isRecursive(cmd) && findRmOperand(cmd, isSensitive) != null);
    }

    /**
     * 普通路径的递归强制删除：放行但告警
     */
    private static CommandRule rmRecursiveForce() {
        return new CommandRule("FILE_RM_RF", MEDIUM, "文件破坏",
                cmd -> "递归强制删除: " + cmd.getRaw(),
                cmd -> cmd.is("rm") && isRecursive(cmd) && isForce(cmd) && !cmd.getOperands().isEmpty());
    }

    /**
     * 递归删除的目标来自管道输入，无法静态校验删除范围
     * <p>
     * 例如 {@code find / -name "*.log" | xargs rm -rf}
     */
    private static CommandRule rmBulkFromPipe() {
        return new CommandRule("FILE_RM_BULK", MEDIUM, "文件破坏",
                cmd -> "递归删除的目标来自管道输入，无法静态校验删除范围: " + cmd.getRaw(),
                cmd -> cmd.is("rm") && isRecursive(cmd) && cmd.getOperands().isEmpty());
    }

    /**
     * 移动 / 拷贝系统关键文件
     */
    private static CommandRule moveCriticalPath() {
        return new CommandRule("FILE_MOVE_CRITICAL", HIGH, "文件破坏",
                cmd -> "移动/拷贝系统关键文件: " + cmd.getRaw(),
                cmd -> cmd.is("mv", "cp", "install", "rsync") && hasCriticalOperand(cmd));
    }

    // ---------- 重定向覆盖 ----------

    /**
     * 输出将覆盖系统关键文件
     */
    private static CommandRule redirectToCriticalPath() {
        return new CommandRule("FILE_REDIRECT_CRITICAL", HIGH, "文件破坏",
                cmd -> "命令输出将覆盖系统关键文件: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream()
                        .anyMatch(t -> SensitivePathPolicy.isCriticalPath(SensitivePathPolicy.normalizePath(t))));
    }

    /**
     * 输出将写入系统目录
     */
    private static CommandRule redirectToSystemPath() {
        return new CommandRule("FILE_REDIRECT_SYSTEM", MEDIUM, "文件破坏",
                cmd -> "命令输出将写入系统目录: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream().anyMatch(t -> {
                    String path = SensitivePathPolicy.normalizePath(t);
                    return path.startsWith("/etc/") || path.startsWith("/boot/");
                }));
    }

    // ---------- 磁盘破坏 ----------

    /**
     * dd 直接写块设备
     */
    private static CommandRule ddWriteBlockDevice() {
        return new CommandRule("FILE_DD_BLOCK_DEVICE", FORBIDDEN, "磁盘破坏",
                cmd -> "dd 直接写块设备，会摧毁磁盘上的数据与分区: of=" + cmd.optionValue("of="),
                cmd -> cmd.is("dd") && cmd.hasOptionValue("of=")
                        && SensitivePathPolicy.isBlockDevice(
                        SensitivePathPolicy.normalizePath(cmd.optionValue("of="))));
    }

    /**
     * 格式化文件系统
     */
    private static CommandRule formatFilesystem() {
        return new CommandRule("FILE_MKFS", FORBIDDEN, "磁盘破坏",
                cmd -> "格式化文件系统会摧毁目标磁盘上的全部数据: " + cmd.getRaw(),
                cmd -> cmd.is("mkfs") || cmd.getBase().startsWith("mkfs.") || cmd.is("mkswap"));
    }

    /**
     * 擦除分区表 / 文件系统签名
     */
    private static CommandRule wipefs() {
        return new CommandRule("FILE_WIPEFS", HIGH, "磁盘破坏",
                cmd -> "wipefs 会擦除分区表/文件系统签名: " + cmd.getRaw(),
                cmd -> cmd.is("wipefs"));
    }

    /**
     * 不可恢复地覆写文件内容
     */
    private static CommandRule shred() {
        return new CommandRule("FILE_SHRED", MEDIUM, "文件破坏",
                cmd -> "shred 会不可恢复地覆写文件内容: " + cmd.getRaw(),
                cmd -> cmd.is("shred"));
    }

    /**
     * 修改磁盘分区表
     */
    private static CommandRule partitionTool() {
        return new CommandRule("FILE_PARTITION", MEDIUM, "磁盘破坏",
                cmd -> "修改磁盘分区表的命令: " + cmd.getRaw(),
                cmd -> cmd.is("fdisk", "sgdisk", "parted"));
    }

    // ---------- 权限与属性 ----------

    /**
     * 把系统 / 业务目录权限改为 others 可写
     */
    private static CommandRule chmodWorldWritable() {
        return new CommandRule("FILE_CHMOD_777", HIGH, "权限与账号",
                cmd -> "把系统/业务目录权限改为 777，等于对所有人开放: " + cmd.getRaw(),
                cmd -> cmd.is("chmod") && hasWorldWritablePermission(cmd) && hasDangerousChmodTarget(cmd));
    }

    /**
     * 修改系统 / 业务目录的所有者
     */
    private static CommandRule chownSystemPath() {
        return new CommandRule("FILE_CHOWN_SYSTEM", MEDIUM, "权限与账号",
                cmd -> "修改系统/业务目录的所有者: " + cmd.getRaw(),
                cmd -> cmd.is("chown") && cmd.getOperands().stream().anyMatch(FileDangerRules::isDangerousChmodTarget));
    }

    /**
     * 修改文件不可变属性
     */
    private static CommandRule chattr() {
        return new CommandRule("FILE_CHATTR", MEDIUM, "文件破坏",
                cmd -> "chattr 可修改文件不可变属性，可能导致文件无法清理或被锁定: " + cmd.getRaw(),
                cmd -> cmd.is("chattr"));
    }

    /**
     * 强制创建符号链接覆盖已有文件
     */
    private static CommandRule lnOverwrite() {
        return new CommandRule("FILE_LN_OVERWRITE", MEDIUM, "文件破坏",
                cmd -> "强制创建符号链接会覆盖已有文件: " + cmd.getRaw(),
                cmd -> cmd.is("ln") && cmd.hasArg("-sf"));
    }

    /**
     * truncate 清空文件内容
     */
    private static CommandRule truncateFile() {
        return new CommandRule("FILE_TRUNCATE", MEDIUM, "文件破坏",
                cmd -> "truncate 会把文件内容清零: " + cmd.getRaw(),
                cmd -> cmd.is("truncate"));
    }

    // ---------- find 直接删除 ----------

    /**
     * find -delete 直接删除匹配到的文件
     */
    private static CommandRule findDelete() {
        return new CommandRule("FILE_FIND_DELETE", HIGH, "文件破坏",
                cmd -> "find -delete 会直接删除匹配到的文件: " + cmd.getRaw(),
                cmd -> cmd.is("find") && cmd.hasArg("-delete"));
    }

    /**
     * find -exec rm 直接删除匹配到的文件
     */
    private static CommandRule findExecRm() {
        return new CommandRule("FILE_FIND_EXEC_RM", HIGH, "文件破坏",
                cmd -> "find -exec rm 会直接删除匹配到的文件: " + cmd.getRaw(),
                cmd -> cmd.is("find") && cmd.hasArg("-exec")
                        && cmd.getRaw().toLowerCase(Locale.ROOT).contains("rm "));
    }

    // ---------- 内部辅助 ----------

    private static boolean isRecursive(ParsedCommand cmd) {
        return cmd.hasShortFlag('r') || cmd.hasLongFlag("recursive");
    }

    private static boolean isForce(ParsedCommand cmd) {
        return cmd.hasShortFlag('f') || cmd.hasLongFlag("force");
    }

    /**
     * 在 rm 的操作数中找出第一个满足条件的路径
     */
    private static String findRmOperand(ParsedCommand cmd, Predicate<String> predicate) {
        if (!cmd.is("rm")) {
            return null;
        }
        for (String operand : cmd.getOperands()) {
            if (predicate.test(SensitivePathPolicy.normalizePath(operand))) {
                return operand;
            }
        }
        return null;
    }

    private static boolean hasCriticalOperand(ParsedCommand cmd) {
        return cmd.getOperands().stream()
                .anyMatch(o -> SensitivePathPolicy.isCriticalPath(SensitivePathPolicy.normalizePath(o)));
    }

    /**
     * chmod 的操作数里既有权限位也有路径，需要成对判断：
     * {@code chmod -R 777 /} 中，777 是权限位，/ 才是路径
     */
    private static boolean hasWorldWritablePermission(ParsedCommand cmd) {
        return cmd.getOperands().stream()
                .filter(SensitivePathPolicy::isPermission)
                .anyMatch(SensitivePathPolicy::isWorldWritablePermission);
    }

    private static boolean hasDangerousChmodTarget(ParsedCommand cmd) {
        return cmd.getOperands().stream()
                .filter(o -> !SensitivePathPolicy.isPermission(o))
                .anyMatch(FileDangerRules::isDangerousChmodTarget);
    }

    private static boolean isDangerousChmodTarget(String operand) {
        String path = SensitivePathPolicy.normalizePath(operand);
        return "/".equals(path) || SensitivePathPolicy.isSensitivePath(path);
    }

}
