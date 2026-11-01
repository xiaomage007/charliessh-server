package com.charlie.ai.infrastructure.adapter.port;

import com.charlie.ai.domain.ssh.adapter.port.ITerminalSessionPort;
import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端会话管理器
 * 基础设施层实现，管理 Shell 通道的创建、读写、关闭
 */
@Slf4j
@Component
public class TerminalSessionPort implements ITerminalSessionPort {

    @Resource
    private SshSessionPort sshSessionService;

    /** sessionId -> Shell 通道 */
    private final Map<String, ChannelShell> channels = new ConcurrentHashMap<>();

    /** sessionId -> 输出流 */
    private final Map<String, OutputStream> outputStreams = new ConcurrentHashMap<>();

    /** sessionId -> 输入流 */
    private final Map<String, InputStream> inputStreams = new ConcurrentHashMap<>();

    /** sessionId -> 未读输出缓冲区 */
    private final Map<String, StringBuilder> outputBuffers = new ConcurrentHashMap<>();

    /** sessionId -> 读取线程是否存活 */
    private final Map<String, Boolean> readerAlive = new ConcurrentHashMap<>();

    /** connectionId -> 当前活跃的 sessionId（一个连接只允许一个终端会话） */
    private final Map<String, String> activeConnectionSession = new ConcurrentHashMap<>();

    @Override
    public String openTerminal(String connectionId, int cols, int rows) {
        // 同一 connectionId 只允许一个终端会话，先关闭旧的
        String oldSessionId = activeConnectionSession.get(connectionId);
        if (oldSessionId != null) {
            log.info("关闭旧终端会话以避免重复 connectionId={} oldSessionId={}", connectionId, oldSessionId);
            cleanup(oldSessionId);
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");

        try {
            Session session = sshSessionService.getSession(connectionId);
            if (session == null || !session.isConnected()) {
                throw new IllegalStateException("SSH会话不可用 connectionId=" + connectionId);
            }

            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);
            channel.setPtySize(cols, rows, 480, 640);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            channel.connect(5000);

            channels.put(sessionId, channel);
            inputStreams.put(sessionId, in);
            outputStreams.put(sessionId, out);
            outputBuffers.put(sessionId, new StringBuilder());
            activeConnectionSession.put(connectionId, sessionId);

            // 启动输出读取线程，持续读取 shell 输出到缓冲区
            startOutputReader(sessionId, in);

            // 等待 Shell 首次输出到达 + 额外等待让 MOTD 完整积累
            // 然后消费缓冲区，作为 initialOutput 返回给前端
            // 这样前端不再依赖轮询获取初始输出，彻底解决"有时显示有时不显示"的问题
            StringBuilder buffer = outputBuffers.get(sessionId);
            long waitDeadline = System.currentTimeMillis() + 3000; // 最多等 3s 等首数据
            try {
                // 阶段1：等首数据到达
                while (System.currentTimeMillis() < waitDeadline) {
                    synchronized (buffer) {
                        if (!buffer.isEmpty()) {
                            break;
                        }
                    }
                    Thread.sleep(30);
                }
                // 阶段2：额外等 200ms 让 MOTD/prompt 完整到达
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            log.info("终端会话打开成功 sessionId={} connectionId={}", sessionId, connectionId);
            return sessionId;

        } catch (Exception e) {
            log.error("打开终端会话失败 connectionId={}", connectionId, e);
            cleanup(sessionId);
            throw new RuntimeException("打开终端失败: " + e.getMessage(), e);
        }
    }

    /** 输入重试次数 */
    private static final int WRITE_MAX_RETRIES = 2;

    /** Write with retry on I/O failure */
    @Override
    public void write(String sessionId, String command) {
        OutputStream out = outputStreams.get(sessionId);
        if (out == null) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        IOException lastError = null;
        for (int attempt = 1; attempt <= WRITE_MAX_RETRIES; attempt++) {
            try {
                out.write(command.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return; // 成功
            } catch (IOException e) {
                lastError = e;
                log.warn("写入终端失败 (attempt={}/{}) sessionId={} reason={}",
                        attempt, WRITE_MAX_RETRIES, sessionId, e.getMessage());
                // 最后一次重试后抛出异常
                if (attempt == WRITE_MAX_RETRIES) {
                    break;
                }
                // 短暂等待后重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("写入终端失败 (已重试 {} 次) sessionId={}", WRITE_MAX_RETRIES, sessionId, lastError);
        throw new RuntimeException("写入终端失败: " + lastError.getMessage(), lastError);
    }

    /**
     * 非交互式执行一条命令（exec 通道）
     * <p>
     * 【为什么 AI 工具必须走 exec 通道，而不是复用交互式 shell 通道】
     * <pre>
     * shell 通道（交互式）：
     *   write("df -h")  → PTY canonical 模式必须收到 \n 才会提交给 shell
     *                   → 不带 \n 时命令【根本不会执行】，终端只回显你输入的字符
     *   read()         → 非阻塞快照，返回的是"上一条命令"的回显，天然滞后一拍
     *
     * exec 通道（非交互式）：
     *   setCommand(cmd) + connect() → 服务端直接执行
     *   命令结束 → stdout/stderr 关闭 → getExitStatus() 拿到真实退出码
     *   输出与命令严格一一对应，不存在回显、不存在提示符、不存在错位
     * </pre>
     * 实现要点：
     * <ol>
     *     <li>stdout / stderr 必须<b>同时消费</b>——只读一个会让另一个的缓冲区写满，
     *         阻塞远端命令，命令永远跑不完；</li>
     *     <li>用「通道是否关闭」判断命令是否执行完毕，而不是靠固定 sleep 猜时间；</li>
     *     <li>整体超时（含 connect 耗时），超时后主动 disconnect，避免僵尸通道。</li>
     * </ol>
     */
    @Override
    public TerminalExecResult execCommand(String connectionId, String command, int timeoutSeconds) {
        Session session = sshSessionService.getSession(connectionId);
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH 连接不可用，请先建立连接 connectionId=" + connectionId);
        }

        long start = System.currentTimeMillis();
        long timeoutMs = Math.max(timeoutSeconds, 1) * 1000L;
        long deadline = start + timeoutMs;

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        ChannelExec channel = null;

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            // 与 shell 通道保持一致：先取流再 connect，避免首批数据丢失
            InputStream outStream = channel.getInputStream();
            InputStream errStream = channel.getErrStream();

            channel.connect((int) Math.min(timeoutMs, Integer.MAX_VALUE));

            // stdout / stderr 各起一个读取线程，必须并发消费
            Thread outReader = startStreamReader(outStream, stdout, "ssh-exec-stdout");
            Thread errReader = startStreamReader(errStream, stderr, "ssh-exec-stderr");

            // 等待通道关闭 = 命令执行完毕
            boolean finished = waitForChannelClose(channel, deadline);
            boolean timeout = !finished;
            if (timeout) {
                log.warn("SSH 命令执行超时，主动断开 command={} timeout={}s", command, timeoutSeconds);
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                    // 断开失败不影响返回结果
                }
            }

            joinQuietly(outReader);
            joinQuietly(errReader);

            int exitCode = channel.getExitStatus();
            long duration = System.currentTimeMillis() - start;

            log.info("SSH exec 完成 command={} exitCode={} timeout={} duration={}ms stdoutLen={} stderrLen={}",
                    command, exitCode, timeout, duration, stdout.length(), stderr.length());

            return TerminalExecResult.builder()
                    .command(command)
                    .stdout(stdout.toString())
                    .stderr(stderr.toString())
                    .exitCode(exitCode)
                    .timeout(timeout)
                    .durationMs(duration)
                    .build();

        } catch (JSchException e) {
            log.error("SSH exec 失败 command={} connectionId={}", command, connectionId, e);
            throw new RuntimeException("执行 SSH 命令失败: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("SSH exec 流异常 command={} connectionId={}", command, connectionId, e);
            throw new RuntimeException("执行 SSH 命令失败: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 启动流读取线程（daemon，避免阻塞 JVM 退出）
     */
    private Thread startStreamReader(InputStream in, StringBuilder sink, String threadName) {
        Thread thread = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int len;
                while ((len = in.read(buf)) != -1) {
                    if (len > 0) {
                        sink.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                // 通道被断开属于正常收尾，不作为错误处理
                log.debug("exec 流读取结束 thread={} reason={}", threadName, e.getMessage());
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 等待通道关闭（命令结束），超过 deadline 返回 false 表示超时
     */
    private boolean waitForChannelClose(ChannelExec channel, long deadline) {
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * 等待读取线程结束，最多等 1 秒（此时通道已断，流理论上已 EOF）
     */
    private void joinQuietly(Thread thread) {
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String read(String sessionId) {
        StringBuilder buffer = outputBuffers.get(sessionId);
        if (buffer == null) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        Boolean alive = readerAlive.get(sessionId);
        if (alive != null && !alive) {
            ChannelShell channel = channels.get(sessionId);
            if (channel == null || !channel.isConnected()) {
                return "\u001b[31m\r\n[连接已断开]\u001b[0m\r\n";
            }
            InputStream in = inputStreams.get(sessionId);
            if (in != null) {
                log.info("尝试重启终端读取线程 sessionId={}", sessionId);
                startOutputReader(sessionId, in);
            }
        }

        // 非阻塞：直接返回缓冲区当前内容，不等
        // 前端轮询本身就是等待机制，不需要后端再等
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return "";
            }
            String output = buffer.toString();
            buffer.setLength(0);
            return output;
        }
    }

    @Override
    public void resize(String sessionId, int cols, int rows) {
        ChannelShell channel = channels.get(sessionId);
        if (channel == null || !channel.isConnected()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        try {
            channel.setPtySize(cols, rows, 480, 640);
            log.debug("终端大小已调整 sessionId={} {}x{}", sessionId, cols, rows);
        } catch (Exception e) {
            log.error("调整终端大小失败 sessionId={}", sessionId, e);
            throw new RuntimeException("调整终端大小失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void closeSession(String sessionId) {
        log.info("关闭终端会话 sessionId={}", sessionId);
        cleanup(sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        ChannelShell channel = channels.get(sessionId);
        return channel != null && channel.isConnected();
    }

    // ========== 内部方法 ==========

    /**
     * 启动输出读取线程
     * SocketTimeoutException 时继续循环（不是真正的断连），
     * 只有 EOF（-1）或真正的 IOException 才退出
     */
    private void startOutputReader(String sessionId, InputStream in) {
        readerAlive.put(sessionId, true);
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int len;
                while ((len = in.read(buf)) != -1) {
                    String text = new String(buf, 0, len, StandardCharsets.UTF_8);
                    StringBuilder buffer = outputBuffers.get(sessionId);
                    if (buffer != null) {
                        synchronized (buffer) {
                            buffer.append(text);
                        }
                    }
                }
                // in.read() 返回 -1，说明 shell channel EOF
                log.warn("终端 Shell Channel EOF sessionId={}", sessionId);
            } catch (java.net.SocketTimeoutException e) {
                // SocketTimeout 不是断连，重试即可——但实际不应到这里了（setTimeout(0)）
                // 保留作为防御性处理
                log.debug("终端读取超时（非断连），继续读取 sessionId={}", sessionId);
                InputStream inRef = inputStreams.get(sessionId);
                if (inRef != null) {
                    startOutputReader(sessionId, inRef);
                }
                return; // 当前线程退出，新线程已接替
            } catch (IOException e) {
                ChannelShell ch = channels.get(sessionId);
                // channel 还活着说明不是真正的断连，可能是临时 I/O 问题，尝试重启
                if (ch != null && ch.isConnected()) {
                    log.warn("终端读取I/O异常但channel仍连接，重启reader sessionId={} reason={}", sessionId, e.getMessage());
                    InputStream inRef = inputStreams.get(sessionId);
                    if (inRef != null) {
                        startOutputReader(sessionId, inRef);
                    }
                    return;
                }
                log.debug("终端输出读取异常 sessionId={} reason={}", sessionId, e.getMessage());
            } finally {
                readerAlive.put(sessionId, false);

                // 诊断：为什么线程退出了？
                ChannelShell ch = channels.get(sessionId);
                boolean channelConnected = ch != null && ch.isConnected();
                boolean channelClosed = ch != null && ch.isClosed();
                log.warn("终端输出读取线程退出 sessionId={} channelConnected={} channelClosed={}",
                        sessionId, channelConnected, channelClosed);

                // 通知等待中的 read() 方法：线程已退出，不再有数据
                StringBuilder buffer = outputBuffers.get(sessionId);
                if (buffer != null) {
                    synchronized (buffer) {
                        buffer.notifyAll();
                    }
                }
            }
        }, "terminal-reader-" + sessionId);
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * 清理资源
     */
    private void cleanup(String sessionId) {
        // 清理 connectionId -> sessionId 映射
        activeConnectionSession.entrySet().removeIf(e -> sessionId.equals(e.getValue()));

        try {
            OutputStream out = outputStreams.remove(sessionId);
            if (out != null) out.close();
        } catch (IOException ignored) {}

        try {
            InputStream in = inputStreams.remove(sessionId);
            if (in != null) in.close();
        } catch (IOException ignored) {}

        ChannelShell channel = channels.remove(sessionId);
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }

        outputBuffers.remove(sessionId);
        readerAlive.remove(sessionId);
    }

}
