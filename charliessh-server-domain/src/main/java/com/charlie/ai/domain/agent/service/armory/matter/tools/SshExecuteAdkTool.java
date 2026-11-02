package com.charlie.ai.domain.agent.service.armory.matter.tools;

import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import com.google.adk.tools.Annotations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description: SSH 命令执行 ADK 工具，为智能体提供在 SSH 终端执行命令的能力
 * 使用 ADK 的 @Schema 注解定义参数，支持 FunctionTool.create()
 * @Author: Charlie
 * @Date: 2026/10/31 11:26
 */
@Slf4j
@Service
public class SshExecuteAdkTool {

    @Resource
    private ISshTerminalService sshTerminalService;

    /**
     * 单条命令执行超时（秒）
     */
    @Value("${ssh.agent.exec.timeout-seconds:60}")
    private int execTimeoutSeconds;

    /**
     * 工具回传给模型的输出上限（字符），超出部分截断，避免撑爆模型上下文
     */
    @Value("${ssh.agent.exec.max-output-length:4000}")
    private int maxOutputLength;

    /**
     * ============================================================================
     * 一、什么是 InheritableThreadLocal
     * ============================================================================
     * ThreadLocal 是"线程封闭"容器，保证每个线程各持有一份互不可见的副本。
     * InheritableThreadLocal 继承自 ThreadLocal，只多了一条能力：
     * 【子线程创建时，自动继承父线程中该 InheritableThreadLocal 持有的值】。
     * <p>
     * 二、底层原理（对照 JDK 源码看）
     * ---------------------------------------------------------------------------
     * 1) 值并不存放在 ThreadLocal 对象里，而是存在【当前线程对象】自己的
     * ThreadLocalMap 中：
     * - Thread.threadLocals               : ThreadLocalMap（普通 ThreadLocal）
     * - Thread.inheritableThreadLocals    : ThreadLocalMap（可继承 ThreadLocal）
     * ThreadLocalMap 底层是 Entry[] 开放寻址（hash 冲突线性探测）：
     * static class Entry extends WeakReference<ThreadLocal<?>> {
     * Object value;      // 真正的业务值，被强引用
     * }
     * key 是 ThreadLocal 实例本身且为【弱引用】，value 为强引用。
     * <p>
     * 2) get/set 的本质只是"操作当前线程的那张 map"：
     * ThreadLocal.get()  ->  thread.threadLocals.getEntry(this)
     * ThreadLocal.set()  ->  map.set(this, value)
     * 同一个 ThreadLocal 实例作为 key 出现在不同线程各自独立的 map 中，
     * 所以天然互不可见 —— 这就是"线程隔离"的真相，而不是 ThreadLocal 复制了变量。
     * <p>
     * 3) 继承动作发生在【子线程构造的那一刻】（JDK Thread 构造函数）：
     * if (parent.inheritableThreadLocals != null) {
     * this.inheritableThreadLocals =
     * ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
     * }
     * createInheritedMap 为父 map 中每个有效 Entry 新建一个 Entry，
     * 新 Entry 的 value 来自 parentThreadLocal.childValue(parentValue)。
     * <p>
     * 4) childValue 默认实现是【浅拷贝】：
     * protected T childValue(T parentValue) { return parentValue; }
     * - 不可变值（如 String）：子线程 set 只改自己的 map，父子互不影响；
     * - 可变值（如集合）：子线程改动内部元素，父线程同样能看到。
     * 需要隔离时覆写 childValue 返回副本即可。
     * <p>
     * 三、三条必须记住的结论（直接决定本项目怎么用）
     * ---------------------------------------------------------------------------
     * ① 继承只发生【一次】，时点是【子线程被 new 出来的瞬间】。
     * → 必须先 set 再创建线程才有效；若线程先创建（线程池预热）后 set，
     * 子线程永远拿不到值。
     * ② 【线程池场景完全失效】。
     * 池中 worker 线程提前创建并被反复复用，继承只发生过一次；此后每个任务
     * 读到的都是"上一个任务残留"的值 —— 典型的会话串号事故。
     * → 线程池请改用阿里的 TransmittableThreadLocal(TTL) 配合
     * TtlExecutors.getTtlExecutorService()，或 Spring TaskDecorator
     * （任务提交时快照、结束时还原）；最稳妥的做法仍是【显式参数传递】。
     * ③ 【必须 remove()】。
     * - 线程被复用时，未清理的脏值会被下一个任务读到；
     * - value 是强引用，长期不释放会让 Session / RequestContext 等大对象
     * 无法回收（ThreadLocal 实例为静态时 key 不会被回收，value 更不会）。
     * → 规范写法：try { ... } finally { threadLocal.remove(); }
     * <p>
     * 四、本类的用法：把 SSH 终端会话 ID 跨线程传给 ADK 工具
     * ---------------------------------------------------------------------------
     * 场景：sessionId 在 HTTP/Agent 入口线程中确定，而 ADK 的 Runner、LLM 调用、
     * 工具执行可能在它自己创建的线程里跑，工具方法拿不到入口的局部变量。
     * <p>
     * 当前项目（ChatService.handleMessageStream）的实际做法：
     * <pre>
     * return Flowable.defer(() -> {
     *            SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
     *            return runner.runAsync(userId, sessionId, userMsg);
     *        })
     *        .doFinally(() -> SshExecuteAdkTool.clearCurrentTerminalSession());
     * </pre>
     * 三个要点：
     * <ol>
     *     <li><b>set 必须在 defer 里</b>：ADK 的 runAsync 返回 cold Flowable，
     *         事件流在【订阅时】才开始。若在方法返回前就 set，而订阅发生在别的线程，
     *         工具执行时就读不到（InheritableThreadLocal 只在线程创建时继承一次）。
     *         放进 defer 后，set 与工具执行落在同一个订阅线程上。</li>
     *     <li><b>必须 remove</b>：Web 容器 / 线程池会复用线程。不清理的话，
     *         下一个请求会读到上一个请求残留的 terminalSessionId，
     *         导致命令被发到【别人的服务器】上执行——这是安全问题，不是洁癖。
     *         doFinally 覆盖正常结束 / 异常 / 取消三条收尾路径。</li>
     *     <li><b>读不到就明确报错</b>：工具内 get 到 null 时直接返回"未绑定 SSH 终端会话"，
     *         绝不 fallback 到某个"默认会话"，避免误操作到错误的机器。</li>
     * </ol>
     * <p>
     * 补充：若 ADK 内部走的是响应式/Reactor 线程池（工具执行线程 ≠ 订阅线程），
     * 上面的继承关系就不成立，需要改用 Reactor Context 透传，或干脆把 sessionId
     * 作为工具参数显式传给模型（更可控，代价是模型可能填错）。
     */
    private static final InheritableThreadLocal<String> currentTerminalSession = new InheritableThreadLocal<>();

    /**
     * 终端 ANSI 控制序列（颜色、光标移动、光标显隐等）
     * 形如 ESC[31m、ESC[1;1H、ESC[?2004h，匹配关键词前必须先剔除
     */
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[0-9;?]*[ -/]*[@-~]");

    /**
     * 退出码特征：exit code: 1 / exit status 1 / exited with code 1 / return code 1
     * 单独处理是为了区分成功（0）与失败（非 0），避免 "exit code: 0" 被误判为错误
     */
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile(
            "(?:exit code|exit status|exit with code|exited with code|return code|returned exit code)\\D{0,4}(\\d+)");

    /**
     * 当前会话级终端会话ID（由 Controller 设置，优先级低于 ThreadLocal）
     */
    private static volatile String sessionTerminalSessionId;

    /**
     * 写入当前线程的终端会话 ID。
     * 子线程在此之后被创建时，会通过 childValue 继承到该值。
     *
     * @param terminalSessionId 终端会话 ID
     */
    public static void setCurrentTerminalSession(String terminalSessionId) {
        currentTerminalSession.set(terminalSessionId);
        sessionTerminalSessionId = terminalSessionId;
        log.info("[ThreadLocal] 设置终端会话: thread={}, terminalSession={}", Thread.currentThread().getName(), terminalSessionId);
    }

    /**
     * 读取当前线程可见的终端会话 ID。
     * 返回值可能来自本线程设置，也可能来自父线程继承。
     *
     * @return 终端会话 ID；无上下文时返回 null
     */
    public static String getCurrentSession() {
        return currentTerminalSession.get();
    }

    /**
     * 清理当前线程的会话上下文。
     * 必须在 finally 中调用：防止线程复用时脏值外泄，同时释放 value 的强引用避免内存泄漏。
     */
    public static void clearCurrentTerminalSession() {
        currentTerminalSession.remove();
        sessionTerminalSessionId = null;
    }

    public Map<String, Object> executeCommand(@Annotations.Schema(name = "command", description = "要执行的 Shell 命令，如: ls -la, apt install docker.io, docker --version") String command) {
        // 优先从 ThreadLocal 获取，支持异步线程继承
        String terminalSessionId = getCurrentSession();

        // ThreadLocal 为空时回退到会话级变量（线程池场景下 ThreadLocal 可能失效）
        if (terminalSessionId == null || terminalSessionId.isEmpty()) {
            terminalSessionId = sessionTerminalSessionId;
            log.info("[executeCommand] ThreadLocal 为空，回退到会话级变量: terminalSessionId={}", terminalSessionId);
        }

        log.info("[executeCommand] thread={}, terminalSessionId={}, command={}", Thread.currentThread().getName(), terminalSessionId, command);

        if (terminalSessionId == null || terminalSessionId.isEmpty()) {
            log.warn("[executeCommand] 终端会话ID为空，无法执行命令");
            return Map.of("success", false, "output", "未绑定 SSH 终端会话。请先打开 SSH 终端连接。", "command", command);
        }

        if (!sshTerminalService.sessionExists(terminalSessionId)) {
            log.warn("[executeCommand] 终端会话不存在: {}", terminalSessionId);
            return Map.of("success", false, "output", "SSH 终端会话不存在或已关闭: " + terminalSessionId, "command", command);
        }

        try {
            log.info("SSH 执行命令: session={}, command={}", terminalSessionId, command);

            // 走 exec 通道：命令真正执行完才返回，输出与命令严格对应，并带真实退出码
            TerminalExecResult result = sshTerminalService.executeCommand(
                    terminalSessionId, command, execTimeoutSeconds);

            String rawOutput = result.getOutput();
            String output = truncateOutput(rawOutput);

            log.info("SSH 命令执行完成: exitCode={}, timeout={}, duration={}ms, outputLength={}, output={}",
                    result.getExitCode(), result.isTimeout(), result.getDurationMs(),
                    rawOutput.length(), output);

            // 成功判定：退出码优先；退出码未知（-1）时回退到关键词判断
            boolean success = result.isSuccess();
            if (result.getExitCode() < 0 && !result.isTimeout()) {
                success = isExecutionSuccessful(rawOutput, command);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("command", command);
            resp.put("output", output);
            resp.put("success", success);
            resp.put("exitCode", result.getExitCode());

            if (result.isTimeout()) {
                resp.put("timeout", true);
                resp.put("suggestion", "命令执行超过 " + execTimeoutSeconds + " 秒仍未结束，已被中断。"
                        + "建议：①确认命令本身不会长时间阻塞（如 apt update、tail -f、watch）；"
                        + "②改用带条件的后台方式执行；③确实需要更长等待时间时，请让用户确认后调大 ssh.agent.exec.timeout-seconds。");
            } else if (!success) {
                resp.put("suggestion", analyzeError(rawOutput, command, result.getExitCode()));
            }

            return resp;

        } catch (Exception e) {
            log.error("SSH 命令执行异常: session={}, command={}", terminalSessionId, command, e);
            return Map.of("success", false, "output", "命令执行异常: " + e.getMessage(), "command", command);
        }
    }

    /**
     * 截断过长输出
     * <p>
     * 保留<b>头部</b>与<b>尾部</b>：头部通常是命令的正常输出，尾部通常是真正的报错原因，
     * 中间部分用省略号标记。这样既不撑爆模型上下文，又不丢掉关键信息。
     */
    private String truncateOutput(String output) {
        if (output == null) {
            return "";
        }
        int limit = Math.max(maxOutputLength, 500);
        if (output.length() <= limit) {
            return output;
        }
        int headLength = (int) (limit * 0.7);
        int tailLength = limit - headLength;
        return output.substring(0, headLength)
                + "\n...[输出过长，中间 " + (output.length() - limit) + " 字符已省略]...\n"
                + output.substring(output.length() - tailLength);
    }


    /**
     * 判断命令执行是否成功
     * <p>
     * 判定方式：把终端输出归一化后，逐个匹配"错误特征词"，命中任意一个即视为失败。
     * <p>
     * 【为什么要先归一化】这里拿到的是 PTY 原始终端输出，里面混着三类噪声：
     * 1) ANSI 控制序列：颜色、光标移动等，如 ESC[31m、ESC[?2004h；
     * 2) 提示符：root@host:/opt# 之类；
     * 3) 【命令回显】：shell 会把输入的命令原样打印回来。
     * 第 3 类最致命 —— 执行 {@code grep error a.txt} 时输出里含 "error"，
     * 执行 {@code cat makefile} 时输出里含 "makefile"，
     * 不剔除就会把"命令自身携带的关键词"误判成错误。
     *
     * @param output  终端原始输出
     * @param command 本次执行的命令（用于剔除回显）
     * @return true 表示未发现错误特征
     */
    private boolean isExecutionSuccessful(String output, String command) {
        if (output == null || output.isEmpty()) {
            return true;
        }

        String normalized = normalizeForMatch(output, command);

        // 退出码优先判定：非 0 一定失败，0 一定成功。
        // 单独处理是为了避免 "exit code: 0" 这类成功输出被后面的通用关键词误判。
        Matcher exitMatcher = EXIT_CODE_PATTERN.matcher(normalized);
        while (exitMatcher.find()) {
            if (!"0".equals(exitMatcher.group(1))) {
                return false;
            }
        }

        String[] errorIndicators = {
                // ---------- 1. 命令 / 文件 / 资源不存在 ----------
                "command not found",
                "command not recognised",
                "is not recognized as an internal or external command",
                "no such file or directory",
                "no such file",
                "no such device",
                "no such device or address",
                "no such user",
                "no such host",
                "no such process",
                "no such container",
                "no such image",
                "no such package",
                "no such device or address",
                "no matching",
                "cannot find",
                "could not find",
                "couldn't find",
                "does not exist",
                "not exist",
                "not found in path",
                "unknown command",
                "unknown option",
                "unknown argument",
                "unknown flag",
                "unknown host",
                "unknown error",
                "unrecognized command",
                "unrecognized option",
                "unrecognized argument",
                "unrecognized flag",
                "not installed",
                "modulenotfounderror",
                "no module named",
                // ---------- 2. 权限 / 认证 ----------
                "permission denied",
                "access denied",
                "operation not permitted",
                "not permitted",
                "not allowed",
                "you are not authorized",
                "unauthorized",
                "forbidden",
                "authentication failed",
                "auth failed",
                "invalid credentials",
                "bad credentials",
                "no supported authentication methods",
                "host key verification failed",
                "too many authentication failures",
                "root login refused",
                // ---------- 3. 参数 / 用法 / 语法 ----------
                "invalid argument",
                "invalid option",
                "invalid parameter",
                "invalid syntax",
                "illegal option",
                "unexpected argument",
                "unexpected token",
                "unexpected eof",
                "bad flag",
                "too many arguments",
                "missing argument",
                "missing required",
                "required argument",
                "required parameter",
                "syntax error",
                "parse error",
                "parsing error",
                "usage:",
                "try '",
                // ---------- 4. 资源 / 容量 / 存储 ----------
                "no space left on device",
                "disk quota exceeded",
                "out of memory",
                "out of disk space",
                "cannot allocate memory",
                "too many open files",
                "too many connections",
                "resource busy",
                "device or resource busy",
                "text file busy",
                "read-only file system",
                "readonly file system",
                "address already in use",
                "file exists",
                "cannot create",
                "cannot remove",
                "cannot open",
                "cannot start",
                "cannot write",
                "cannot mount",
                "cannot install",
                // ---------- 5. 网络 / 连接 ----------
                "connection refused",
                "connection reset",
                "connection timed out",
                "connection closed",
                "connection aborted",
                "lost connection",
                "broken pipe",
                "no route to host",
                "network is unreachable",
                "network is down",
                "host is unreachable",
                "host is down",
                "name or service not known",
                "temporary failure in name resolution",
                "could not resolve host",
                "nodename nor servname provided",
                "timed out",
                "timeout exceeded",
                "connection timeout",
                "proxyconnect",
                "ssl connection error",
                "temporary failure",
                // ---------- 6. 进程 / 崩溃 / 退出码 ----------
                "segmentation fault",
                "core dumped",
                "aborted",
                "killed",
                "terminated",
                "non-zero exit",
                "nonzero exit",
                "returned non-zero",
                "exception in thread",
                "java.lang.",
                "caused by:",
                // ---------- 7. 通用错误关键词 ----------
                "error:",
                "error ",
                "(error)",
                "err:",
                "err ",
                "err!",
                "npm err",
                "npm error",
                "yarn error",
                "failed",
                "failure",
                "fatal:",
                "fatal ",
                "panic:",
                "severe:",
                "critical:",
                "unable to",
                "unable ",
                "could not",
                "couldn't",
                "cannot ",
                "can't ",
                "not implemented",
                "unsupported",
                "internal server error",
                "bad gateway",
                "gateway timeout",
                "service unavailable",
                "traceback (most recent call last)",
                "exception",
                // ---------- 8. 中文输出 ----------
                "错误",
                "失败",
                "异常",
                "超时",
                "无效",
                "拒绝访问",
                "拒绝连接",
                "权限不足",
                "找不到",
                "未找到",
                "不存在",
                "无法连接",
                "无法访问",
                "无法创建",
                "空间不足",
                "磁盘空间",
                "内存不足",
                "已存在",
                "未安装",
                "参数错误",
                "语法错误"
        };

        for (String indicator : errorIndicators) {
            if (normalized.contains(indicator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 分析错误并给出可执行的解决建议
     * <p>
     * 优先按<b>退出码</b>定位（最准确），再按"越具体越优先"的关键词匹配；
     * 全部未命中时兜底返回输出中最后一行有效内容，帮助模型自行定位根因。
     *
     * @param output   终端原始输出
     * @param command  本次执行的命令（用于剔除回显）
     * @param exitCode 命令退出码，-1 表示未知
     * @return 建议文本；无输出时返回排查提示
     */
    private String analyzeError(String output, String command, int exitCode) {
        if (output == null || output.isEmpty()) {
            return "命令执行失败，但没有捕获到任何输出。建议：①确认命令是否写对、是否需要以换行结尾提交；②确认 SSH 终端会话仍然存活；③重试一次并观察终端回显。";
        }

        String normalized = normalizeForMatch(output, command);

        // 0. 按退出码定位（最可靠，优先于关键词匹配）
        if (exitCode == 127) {
            return "命令不存在（exit code 127）。建议：①核对命令拼写；②用 which/command -v <命令> 确认是否在 PATH 中；"
                    + "③若软件未安装，先安装（如 apt install / yum install）；④若命令在容器内，改用绝对路径执行。";
        }
        if (exitCode == 126) {
            return "命令存在但不可执行（exit code 126）。建议：①检查文件权限（ls -l），必要时 chmod +x <文件>；"
                    + "②确认执行的是目录或无权限文件；③改用 sudo 执行。";
        }
        if (exitCode > 128) {
            return "命令被信号 " + (exitCode - 128) + " 终止（exit code " + exitCode + "）。"
                    + "建议：①确认是否被 OOM Killer 杀掉（dmesg | tail -20）；②命令是否需要 tty（vim/top 等交互式程序在 exec 通道下不可用）；"
                    + "③是否被 Ctrl+C 或超时中断。";
        }
        if (exitCode > 0) {
            return "命令执行失败，退出码 " + exitCode + "。建议：优先看输出最后几行，那通常才是真正的失败原因；"
                    + "必要时加上调试参数（如 curl -v、docker --debug）重跑一次确认细节。";
        }

        // 1. SSH 认证 / 免密
        if (containsAny(normalized, "authentication failed", "auth failed", "invalid credentials", "bad credentials",
                "no supported authentication methods", "host key verification failed", "too many authentication failures",
                "permission denied (publickey", "root login refused", "认证失败", "身份验证失败", "身份验证错误")) {
            return "SSH 认证失败。建议：①确认用户名与认证方式（密码/密钥）是否正确；②确认公钥已写入目标机 ~/.ssh/authorized_keys 且权限为 600；"
                    + "③host key verification failed 时先执行 ssh-keygen -R <目标IP> 清理旧指纹；④认证失败次数过多会被服务端临时封禁，需等待后再试。";
        }

        // 2. 命令不存在
        if (containsAny(normalized, "command not found", "command not recognised",
                "is not recognized as an internal or external command", "unknown command", "not found in path",
                "modulenotfounderror", "no module named", "未找到命令", "未知命令", "不是内部或外部命令")) {
            return "命令不存在或不在 PATH 中。建议：①核对命令拼写；②确认软件包是否已安装（apt install / yum install / apk add）；"
                    + "③已安装则检查其 bin 目录是否在 PATH 中（echo $PATH）；④该命令在容器内可能需要改用绝对路径。";
        }

        // 3. 文件 / 目录不存在
        if (containsAny(normalized, "no such file or directory", "no such file", "no such device", "no such host",
                "no such user", "no such process", "no such container", "no such image", "cannot find", "could not find",
                "couldn't find", "does not exist", "not exist", "找不到", "未找到", "不存在")) {
            return "文件或目录不存在。建议：①用 ls / pwd 确认目标路径是否真实存在；②检查路径拼写与大小写（Linux 区分大小写）；"
                    + "③改用绝对路径；④注意相对路径是相对于当前工作目录，而非你以为的目录。";
        }

        // 4. 权限不足
        if (containsAny(normalized, "permission denied", "access denied", "operation not permitted", "not permitted",
                "not allowed", "you are not authorized", "unauthorized", "forbidden", "权限不足", "拒绝访问", "拒绝")) {
            return "权限不足。建议：①用 sudo 重新执行；②用 ls -l 检查目标文件/目录的 owner 与权限位；"
                    + "③确认当前用户是否在所需用户组中；④若报错来自容器内，注意容器内 root 与宿主机 root 并非同一用户。";
        }

        // 5. 参数 / 用法错误
        if (containsAny(normalized, "invalid argument", "invalid option", "invalid parameter", "invalid syntax",
                "illegal option", "unrecognized option", "unrecognized argument", "unrecognized flag", "unknown option",
                "unknown argument", "unknown flag", "unexpected argument", "bad flag", "too many arguments",
                "missing argument", "missing required", "required argument", "required parameter", "usage:", "try '",
                "参数错误", "未知参数", "无效参数", "用法:")) {
            return "命令参数或用法不正确。建议：①执行该命令的 --help / -h 查看正确用法；②检查参数顺序、取值范围与取值格式；"
                    + "③确认当前版本是否支持该参数（command --version）。";
        }

        // 6. 语法 / 解析错误
        if (containsAny(normalized, "syntax error", "parse error", "parsing error", "unexpected token", "unexpected eof",
                "invalid syntax", "语法错误")) {
            return "命令语法错误或脚本解析失败。建议：①检查引号是否成对、管道符 | 与重定向 > 是否正确；"
                    + "②复杂命令先拆成多步执行；③脚本类报错按提示的行号定位；④含变量的命令注意是否需要转义。";
        }

        // 7. 网络 / 连接
        if (containsAny(normalized, "connection refused", "connection reset", "connection timed out", "connection closed",
                "connection aborted", "lost connection", "broken pipe", "no route to host", "network is unreachable",
                "network is down", "host is unreachable", "host is down", "name or service not known",
                "temporary failure in name resolution", "could not resolve host", "nodename nor servname provided",
                "timed out", "timeout exceeded", "connection timeout", "proxyconnect", "ssl connection error",
                "连接失败", "连接被拒绝", "连接超时", "无法连接", "网络不可达", "解析失败")) {
            return "网络连接失败。建议：①确认目标服务是否已启动（systemctl status <服务> / ps -ef | grep <进程>）；"
                    + "②确认端口是否在监听（ss -lntp）；③检查防火墙与安全组规则；④确认目标 IP/域名可达（ping / nc -zv host port）；"
                    + "⑤DNS 异常时检查 /etc/resolv.conf。";
        }

        // 8. 资源 / 磁盘 / 占用
        if (containsAny(normalized, "no space left on device", "disk quota exceeded", "out of memory", "out of disk space",
                "cannot allocate memory", "too many open files", "too many connections", "resource busy",
                "device or resource busy", "text file busy", "read-only file system", "readonly file system",
                "address already in use", "file exists", "空间不足", "磁盘空间", "内存不足", "已存在", "只读")) {
            return "系统资源不足或目标被占用。建议：①df -h / du -sh 定位磁盘占用并清理；②free -h 查看内存；"
                    + "③确认端口/文件未被其他进程占用（lsof -i :端口）；④只读文件系统需检查挂载参数或重新挂载。";
        }

        // 9. 进程异常退出 / 崩溃
        if (containsAny(normalized, "segmentation fault", "core dumped", "aborted", "killed", "terminated",
                "non-zero exit", "nonzero exit", "returned non-zero", "exception in thread", "java.lang.", "caused by:",
                "被杀死", "进程退出")) {
            return "进程异常退出或被系统终止。建议：①执行 echo $? 查看退出码；②用 dmesg / journalctl 确认是否被 OOM Killer 杀掉；"
                    + "③core dumped 可用 gdb 定位崩溃点；④确认是否被人工中断（Ctrl+C）。";
        }

        // 10. 容器 / Docker
        if (containsAny(normalized, "cannot connect to the docker daemon", "docker:", "no such container", "no such image",
                "image not found", "pull access denied", "manifest unknown", "is not running", "container not found")) {
            return "容器 / 镜像相关错误。建议：①systemctl status docker 确认 dockerd 已运行；②docker images 确认本地镜像存在；"
                    + "③私有仓库需先 docker login；④镜像名与标签是否写错；⑤容器未启动时用 docker ps -a 查看状态与退出码。";
        }

        // 11. 服务 / systemd
        if (containsAny(normalized, "unit not found", "job for", "failed to start", "failed to load", "no such unit")) {
            return "系统服务操作失败。建议：①systemctl list-unit-files | grep <名称> 确认服务名是否正确（注意 .service 后缀）；"
                    + "②systemctl status <服务> 查看详细状态；③查看 journalctl -u <服务> -n 50 获取真实失败原因。";
        }

        // 12. 通用失败
        if (containsAny(normalized, "failed", "failure", "fatal:", "panic:", "severe:", "critical:", "error:",
                "unable to", "could not", "couldn't", "cannot ", "can't ", "not implemented", "unsupported",
                "失败", "错误", "异常")) {
            return "命令执行失败。建议：①优先看输出最后几行，那通常才是真正的失败原因；②确认依赖的服务/进程是否已启动；"
                    + "③用更详细的日志复现（journalctl -u <服务> -n 50 / docker logs <容器>）；④必要时先执行 --help 确认用法。";
        }

        // 13. 兜底：把最后一行有效输出作为线索返回
        String lastLine = lastMeaningfulLine(output);
        if (!lastLine.isEmpty()) {
            return "执行失败，请检查命令与输出信息。最后一行输出：" + lastLine;
        }
        return "执行失败，请检查命令和输出信息。";
    }

    /**
     * 归一化输出，供关键词匹配使用
     * 1) 去掉 ANSI 控制序列
     * 2) 去掉命令回显（提示符后由 shell 打印出来的命令本身）
     * 3) 转小写
     */
    private String normalizeForMatch(String output, String command) {
        String cleaned = ANSI_PATTERN.matcher(output).replaceAll("");

        if (command != null && !command.trim().isEmpty()) {
            String trimmedCommand = command.trim();
            int echoIndex = findCommandEchoIndex(cleaned, trimmedCommand);
            if (echoIndex >= 0) {
                cleaned = cleaned.substring(0, echoIndex)
                        + cleaned.substring(echoIndex + trimmedCommand.length());
            }
        }
        return cleaned.toLowerCase();
    }

    /**
     * 定位命令回显在输出中的位置
     * 回显的特征是"一整行内容恰好等于该命令"，因此只在【命令后面紧跟换行】时才认定是回显，
     * 避免误删真实输出中恰好包含该命令的片段。
     */
    private int findCommandEchoIndex(String text, String command) {
        int from = 0;
        while (true) {
            int idx = text.indexOf(command, from);
            if (idx < 0) {
                return -1;
            }
            int end = idx + command.length();
            boolean lineEnd = end >= text.length() || text.charAt(end) == '\r' || text.charAt(end) == '\n';
            if (lineEnd) {
                return idx;
            }
            from = idx + 1;
        }
    }

    /**
     * 取输出中最后一行有效内容（去掉 ANSI 与空行），作为兜底错误线索
     */
    private String lastMeaningfulLine(String output) {
        if (output == null) {
            return "";
        }
        String cleaned = ANSI_PATTERN.matcher(output).replaceAll("");
        String[] lines = cleaned.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line.length() > 300 ? line.substring(0, 300) + "..." : line;
            }
        }
        return "";
    }

    /**
     * 判断文本是否包含任意一个关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
