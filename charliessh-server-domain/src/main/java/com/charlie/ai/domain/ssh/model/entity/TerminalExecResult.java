package com.charlie.ai.domain.ssh.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端命令执行结果
 * <p>
 * 与交互式终端（shell 通道）不同，本对象对应 <b>exec 通道</b>的执行语义：
 * 命令一次性执行完，服务端回传 stdout / stderr 与退出码，没有回显、没有提示符。
 * <p>
 * 退出码（exitCode）是判断命令成功与否的<b>唯一权威依据</b>：
 * <ul>
 *     <li>{@code 0}    —— 成功</li>
 *     <li>{@code > 0}  —— 失败，值为命令的退出码</li>
 *     <li>{@code -1}   —— 服务端未回传退出码（状态未知），需结合输出判断</li>
 * </ul>
 *
 * @Author: Charlie
 * @Date: 2026/11/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TerminalExecResult {

    /** 本次执行的命令 */
    private String command;

    /** stdout 内容 */
    private String stdout;

    /** stderr 内容 */
    private String stderr;

    /** 命令退出码，-1 表示服务端未回传 */
    private int exitCode;

    /** 是否因超时被中断 */
    private boolean timeout;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /**
     * stdout 与 stderr 合并后的输出
     * <p>
     * stderr 非空时追加在 stdout 之后，并标注来源，方便模型区分
     * "命令输出" 与 "错误信息"。
     */
    public String getOutput() {
        String out = stdout == null ? "" : stdout;
        String err = stderr == null ? "" : stderr;
        if (err.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return err;
        }
        return out + System.lineSeparator() + "[stderr] " + err;
    }

    /**
     * 是否执行成功
     * <p>
     * 超时一律算失败；退出码已知时以退出码为准；
     * 退出码未知（-1）时，只要 stderr 有内容也算失败。
     */
    public boolean isSuccess() {
        if (timeout) {
            return false;
        }
        if (exitCode >= 0) {
            return exitCode == 0;
        }
        return stderr == null || stderr.isBlank();
    }

}
