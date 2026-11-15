package com.charlie.ai.domain.ssh.service.security.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感对象判定策略
 * <p>
 * 把"什么是危险路径 / 危险服务 / 危险权限位"这类<b>环境知识</b>从规则里抽出来，
 * 集中在一处维护。规则只负责"某条命令命中了危险行为"，
 * 而"这个路径算不算敏感"由本类回答。
 * <p>
 * 当前为静态策略（不可变常量集合），后续若需要按环境定制
 * （如把业务目录从敏感集合中剔除），只需改造本类，规则层无需改动。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class SensitivePathPolicy {

    /**
     * 权限位：数字模式 {@code 777} 或符号模式 {@code a+rwx}
     * <p>
     * 注意 {@code [-]} 必须转义，否则 {@code [+-=]} 会被解析成 {@code +} 到 {@code =} 的区间，
     * 从而把 {@code /} 与数字一并吞掉。
     */
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("^[0-7]{3,4}$|^[ugoa]*[+\\-=][rwxXst]*$");

    /**
     * 根目录 / 用户主目录级别：递归删除等同于摧毁整机
     */
    private static final Set<String> ROOT_TARGETS = new HashSet<>(java.util.Arrays.asList(
            "/", "/*", "/.", "/..", "~", "~/", "~/*", "~/.", "$HOME", "${HOME}", "$HOME/", "$HOME/*",
            "*", ".", ".."));

    /**
     * 系统关键文件 / 目录
     */
    private static final Set<String> CRITICAL_PATHS = new HashSet<>(java.util.Arrays.asList(
            "/etc/passwd", "/etc/shadow", "/etc/group", "/etc/gshadow", "/etc/sudoers", "/etc/sudoers.d",
            "/etc/hosts", "/etc/fstab", "/etc/crontab", "/etc/rc.local", "/etc/ld.so.preload",
            "/etc/ld.so.conf", "/etc/ssh", "/etc/ssh/sshd_config", "/etc/ssh/sshd_config.d",
            "/etc/profile", "/etc/profile.d", "/etc/bash.bashrc", "/etc/crontab.d",
            "/boot", "/boot/grub", "/boot/grub2", "/root/.ssh/authorized_keys"));

    /**
     * 关键目录（前缀匹配）
     */
    private static final List<String> CRITICAL_DIR_PREFIXES = List.of(
            "/etc/ssh", "/etc/sudoers.d", "/etc/cron", "/etc/profile.d",
            "/etc/ssh/sshd_config.d", "/boot/grub", "/boot/grub2");

    /**
     * 敏感系统目录：其下的递归删除会破坏操作系统或业务数据
     */
    private static final List<String> SENSITIVE_DIRS = List.of(
            "/etc", "/usr", "/bin", "/sbin", "/lib", "/lib64", "/boot", "/sys", "/proc", "/dev",
            "/var/lib", "/var/log", "/var/spool", "/var/www", "/opt", "/srv", "/data", "/root", "/home");

    /**
     * 块设备前缀：写入即破坏磁盘
     */
    private static final List<String> BLOCK_DEVICE_PREFIXES = List.of(
            "/dev/sd", "/dev/nvme", "/dev/vd", "/dev/hd", "/dev/xvd", "/dev/mapper/", "/dev/disk/", "/dev/loop");

    /**
     * 关键系统服务：停掉会直接中断 SSH 通道或业务
     */
    private static final Set<String> CRITICAL_UNITS = new HashSet<>(java.util.Arrays.asList(
            "sshd", "ssh", "ssh-agent", "network", "networkmanager", "networking",
            "firewalld", "iptables", "docker", "docker.socket", "containerd",
            "mysql", "mysqld", "mariadb", "postgresql", "redis", "redis-server",
            "nginx", "rabbitmq", "kafka", "zookeeper", "kubelet", "systemd-journald", "auditd"));

    private SensitivePathPolicy() {
    }

    /**
     * 归一化路径：去尾部斜杠、合并连续斜杠
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String s = path.trim();
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        while (s.contains("//")) {
            s = s.replace("//", "/");
        }
        return s;
    }

    /**
     * 是否为根目录 / 用户主目录级别的删除目标
     */
    public static boolean isRootTarget(String path) {
        return ROOT_TARGETS.contains(path);
    }

    /**
     * 是否为系统关键文件或目录
     */
    public static boolean isCriticalPath(String path) {
        if (path.isEmpty()) {
            return false;
        }
        if (CRITICAL_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : CRITICAL_DIR_PREFIXES) {
            if (path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return path.contains("authorized_keys");
    }

    /**
     * 是否位于敏感系统目录之下
     */
    public static boolean isSensitivePath(String path) {
        if (path.isEmpty()) {
            return false;
        }
        for (String dir : SENSITIVE_DIRS) {
            if (path.equals(dir) || path.startsWith(dir + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为块设备
     */
    public static boolean isBlockDevice(String path) {
        for (String prefix : BLOCK_DEVICE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为关键系统服务（忽略 .service 后缀）
     */
    public static boolean isCriticalServiceUnit(String unit) {
        String name = unit.toLowerCase(Locale.ROOT);
        if (name.endsWith(".service")) {
            name = name.substring(0, name.length() - ".service".length());
        }
        return CRITICAL_UNITS.contains(name);
    }

    /**
     * 是否为权限位写法（数字模式或符号模式）
     */
    public static boolean isPermission(String token) {
        return token != null && PERMISSION_PATTERN.matcher(token).matches();
    }

    /**
     * 是否为"其他用户可写"的权限位
     * <p>
     * 数字模式看最后一位（others 位）：{@code 777}/{@code 666} 的 others 可写，
     * {@code 755}/{@code 644} 不可写；符号模式判断是否给 others 授予了写权限。
     */
    public static boolean isWorldWritablePermission(String permission) {
        if (permission.matches("^[0-7]{3,4}$")) {
            char others = permission.charAt(permission.length() - 1);
            return others == '6' || others == '7';
        }
        String p = permission.toLowerCase(Locale.ROOT);
        return p.contains("o+w") || p.contains("a+w") || p.contains("a+rwx") || p.contains("o=rwx");
    }

}
