package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.policy.SensitivePathPolicy;
import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;
import java.util.Set;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.FORBIDDEN;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 系统致命类规则
 * <p>
 * 覆盖：关机重启、init 级别切换、停止关键服务、杀进程、内核模块操作。
 * 这类命令一旦执行，会直接中断 SSH 通道或线上业务，且多数不可回滚。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class SystemDangerRules {

    /**
     * 会导致 systemctl 停服的动作
     */
    private static final Set<String> SERVICE_STOP_ACTIONS = Set.of("stop", "disable", "mask", "kill");

    private SystemDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                powerOff(),
                shutdown(),
                initLevel(),
                systemctlPower(),
                stopCriticalService(),
                killInitProcess(),
                killAllProcess(),
                kernelModule()
        );
    }

    /**
     * reboot / poweroff / halt / telinit
     */
    private static CommandRule powerOff() {
        return new CommandRule("SYS_POWER_OFF", FORBIDDEN, "系统致命",
                cmd -> "关机/重启类命令会直接中断 SSH 通道与在途业务: " + cmd.getBase(),
                cmd -> cmd.is("reboot", "poweroff", "halt", "telinit"));
    }

    /**
     * shutdown 带参数即为真实的关机 / 重启
     * <p>
     * 不带参数的 shutdown 只是查询关机计划，不算危险。
     */
    private static CommandRule shutdown() {
        return new CommandRule("SYS_SHUTDOWN", FORBIDDEN, "系统致命",
                cmd -> "关机/重启类命令会直接中断 SSH 通道与在途业务: " + cmd.getRaw(),
                cmd -> cmd.is("shutdown") && !cmd.getArgs().isEmpty());
    }

    /**
     * init 0 / init 6
     */
    private static CommandRule initLevel() {
        return new CommandRule("SYS_INIT", FORBIDDEN, "系统致命",
                cmd -> "init 0/6 会关机或重启主机: " + cmd.getRaw(),
                cmd -> cmd.is("init") && ("0".equals(cmd.getFirstOperand()) || "6".equals(cmd.getFirstOperand())));
    }

    /**
     * systemctl poweroff / reboot / halt / kexec
     */
    private static CommandRule systemctlPower() {
        return new CommandRule("SYS_SYSTEMCTL_POWER", FORBIDDEN, "系统致命",
                cmd -> "systemctl " + cmd.getFirstOperand() + " 会关机或重启主机: " + cmd.getRaw(),
                cmd -> cmd.is("systemctl", "service")
                        && isPowerAction(cmd.getFirstOperand()));
    }

    /**
     * 停止 / 禁用关键服务
     * <p>
     * 特别地，停掉 sshd 会直接切断当前正在执行的 SSH 通道。
     */
    private static CommandRule stopCriticalService() {
        return new CommandRule("SYS_SYSTEMCTL_STOP_CRITICAL", HIGH, "系统致命",
                cmd -> "停止/禁用关键服务 " + cmd.getSecondOperand() + "，可能中断 SSH 通道与线上业务: " + cmd.getRaw(),
                cmd -> cmd.is("systemctl", "service")
                        && SERVICE_STOP_ACTIONS.contains(cmd.getFirstOperand())
                        && cmd.getSecondOperand() != null
                        && SensitivePathPolicy.isCriticalServiceUnit(cmd.getSecondOperand()));
    }

    /**
     * kill 1 / kill -1：终止 init 进程导致主机立刻宕机
     * <p>
     * PID 为 1 / -1 会被当成选项过滤掉，因此直接扫原始参数。
     */
    private static CommandRule killInitProcess() {
        return new CommandRule("SYS_KILL_INIT", FORBIDDEN, "系统致命",
                cmd -> "kill 1 / kill -1 会终止 init 进程导致主机立刻宕机: " + cmd.getRaw(),
                cmd -> cmd.is("kill") && cmd.getArgs().stream().anyMatch(a -> "-1".equals(a) || "1".equals(a)));
    }

    /**
     * killall -9 / pkill -9
     */
    private static CommandRule killAllProcess() {
        return new CommandRule("SYS_KILL_ALL", MEDIUM, "系统致命",
                cmd -> "强杀进程可能杀掉承载当前会话的进程: " + cmd.getRaw(),
                cmd -> cmd.is("killall", "pkill") && cmd.hasShortFlag('9'));
    }

    /**
     * 内核模块装卸
     */
    private static CommandRule kernelModule() {
        return new CommandRule("SYS_KERNEL_MODULE", MEDIUM, "系统致命",
                cmd -> "内核模块操作可能导致主机崩溃或网络中断: " + cmd.getRaw(),
                cmd -> cmd.is("insmod", "rmmod", "modprobe"));
    }

    private static boolean isPowerAction(String action) {
        return "poweroff".equals(action) || "reboot".equals(action)
                || "halt".equals(action) || "kexec".equals(action);
    }

}
