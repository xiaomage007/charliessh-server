package com.charlie.ai.domain.ssh.service.security.model;

import java.util.List;

/**
 * 单条子命令的解析结果
 * <p>
 * 由 {@code ShellCommandParser} 产出，是所有危险命令规则的<b>唯一输入契约</b>。
 * 规则只依赖本对象提供的能力（基础命令名、参数、重定向目标），
 * 不关心命令是怎么被切分出来的，从而把"解析"与"判定"彻底解耦。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class ParsedCommand {

    /** 原始片段（已归一化） */
    private final String raw;

    /** 基础命令名（小写、无路径），如 {@code /bin/rm} 归一为 {@code rm} */
    private final String base;

    /** 全部参数 */
    private final List<String> args;

    /** 重定向写入的目标 */
    private final List<String> redirects;

    /** 嵌套脚本原文，如 {@code bash -c "xxx"} 中的 xxx */
    private final List<String> nestedScripts;

    public ParsedCommand(String raw, String base, List<String> args,
                         List<String> redirects, List<String> nestedScripts) {
        this.raw = raw;
        this.base = base;
        this.args = args;
        this.redirects = redirects;
        this.nestedScripts = nestedScripts;
    }

    public String getRaw() {
        return raw;
    }

    public String getBase() {
        return base;
    }

    public List<String> getArgs() {
        return args;
    }

    public List<String> getRedirects() {
        return redirects;
    }

    public List<String> getNestedScripts() {
        return nestedScripts;
    }

    /**
     * 基础命令名是否属于给定集合
     */
    public boolean is(String... names) {
        for (String name : names) {
            if (base.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否包含指定短选项，支持组合短选项（{@code -rf} 同时含 r 与 f）
     */
    public boolean hasShortFlag(char flag) {
        char lower = Character.toLowerCase(flag);
        for (String arg : args) {
            if (arg.length() >= 2 && arg.charAt(0) == '-' && arg.charAt(1) != '-') {
                for (int i = 1; i < arg.length(); i++) {
                    if (Character.toLowerCase(arg.charAt(i)) == lower) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 是否包含指定长选项
     */
    public boolean hasLongFlag(String name) {
        for (String arg : args) {
            if (arg.equals("--" + name) || arg.startsWith("--" + name + "=")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 参数中是否出现该取值（精确相等）
     */
    public boolean hasArg(String value) {
        return args.contains(value);
    }

    /**
     * 是否存在形如 {@code key=xxx} 的参数
     */
    public boolean hasOptionValue(String key) {
        return optionValue(key) != null;
    }

    /**
     * 取形如 {@code key=xxx} 的参数值，不存在返回 null
     */
    public String optionValue(String key) {
        for (String arg : args) {
            if (arg.startsWith(key)) {
                return arg.substring(key.length());
            }
        }
        return null;
    }

    /**
     * 非选项参数（路径、子命令名等）
     */
    public List<String> getOperands() {
        List<String> list = new java.util.ArrayList<>(args.size());
        for (String arg : args) {
            if (arg.startsWith("-") && arg.length() > 1) {
                continue;
            }
            list.add(arg);
        }
        return list;
    }

    /**
     * 第一个非选项参数
     */
    public String getFirstOperand() {
        List<String> operands = getOperands();
        return operands.isEmpty() ? null : operands.get(0);
    }

    /**
     * 第二个非选项参数
     */
    public String getSecondOperand() {
        List<String> operands = getOperands();
        return operands.size() < 2 ? null : operands.get(1);
    }

    @Override
    public String toString() {
        return base + " " + String.join(" ", args);
    }

}
