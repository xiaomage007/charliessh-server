package com.charlie.ai.domain.ssh.service.terminal;

import com.charlie.ai.domain.ssh.adapter.port.ISshSessionPort;
import com.charlie.ai.domain.ssh.adapter.port.ITerminalSessionPort;
import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;
import com.charlie.ai.domain.ssh.model.entity.TerminalSessionEntity;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH终端领域服务实现
 * 遵循单一职责原则，将终端会话管理委托给基础设施层
 */
@Slf4j
@Service
public class SshTerminalService implements ISshTerminalService {

    /** 默认命令执行超时（秒） */
    public static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 60;

    private final ISshSessionPort sshSessionService;
    private final ITerminalSessionPort terminalSessionService;

    /** 会话ID -> 终端会话实体 映射 */
    private final Map<String, TerminalSessionEntity> sessionCache = new ConcurrentHashMap<>();

    public SshTerminalService(ISshSessionPort sshSessionService,
                              ITerminalSessionPort terminalSessionService) {
        this.sshSessionService = sshSessionService;
        this.terminalSessionService = terminalSessionService;
    }

    @Override
    public TerminalSessionEntity openTerminal(String connectionId, int cols, int rows) {
        log.info("打开终端会话 connectionId={} cols={} rows={}", connectionId, cols, rows);

        // 1. 检查SSH连接是否已建立
        if (!sshSessionService.isConnected(connectionId)) {
            throw new IllegalStateException("SSH连接未建立，请先连接");
        }

        // 2. 清理同一 connectionId 的旧会话（基础设施层已关闭 channel，这里清理域层缓存）
        sessionCache.entrySet().removeIf(entry -> {
            if (connectionId.equals(entry.getValue().getConnectionId())) {
                log.info("清理旧终端会话缓存 sessionId={} connectionId={}", entry.getKey(), connectionId);
                return true;
            }
            return false;
        });

        // 3. 通过基础设施层打开终端会话
        String sessionId = terminalSessionService.openTerminal(connectionId, cols, rows);

        // 3. 创建并缓存会话实体
        TerminalSessionEntity entity = TerminalSessionEntity.builder()
                .sessionId(sessionId)
                .connectionId(connectionId)
                .cols(cols)
                .rows(rows)
                .status(1)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        sessionCache.put(sessionId, entity);
        log.info("终端会话创建成功 sessionId={}", sessionId);

        return entity;
    }

    @Override
    public String executeCommand(String sessionId, String command) {
        TerminalExecResult result = executeCommand(sessionId, command, DEFAULT_EXEC_TIMEOUT_SECONDS);
        return result.getOutput();
    }

    /**
     * 执行命令并返回完整结果
     * <p>
     * 【为什么不复用交互式 shell 通道】
     * shell 通道是给前端 xterm.js 用的：写进去的是"用户按键"，读出来的是"屏幕内容"。
     * 如果拿它执行 AI 的命令，会遇到三个必然的坑：
     * <ol>
     *     <li>PTY canonical 模式下必须写 {@code \n} 才会把该行提交给 shell，
     *         否则命令根本不执行，终端只回显输入的字符；</li>
     *     <li>shell 通道的 read 是「非阻塞快照」，write 之后立刻 read 拿到的是
     *         上一条命令的残留输出，天然滞后一拍；</li>
     *     <li>输出里混着回显、提示符、ANSI 控制序列，无法可靠判断成功与否。</li>
     * </ol>
     * exec 通道则是"提交命令 → 等它跑完 → 拿 stdout/stderr + 退出码"，
     * 语义与 AI 的使用方式完全吻合。
     */
    @Override
    public TerminalExecResult executeCommand(String sessionId, String command, int timeoutSeconds) {
        log.debug("执行命令 sessionId={} command={} timeout={}s", sessionId, command, timeoutSeconds);

        // 1. 校验会话
        TerminalSessionEntity entity = sessionCache.get(sessionId);
        if (entity == null || !entity.isActive()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭");
        }
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("命令不能为空");
        }

        // 2. 通过 exec 通道执行，阻塞直到命令真正结束
        TerminalExecResult result = terminalSessionService.execCommand(
                entity.getConnectionId(), command, timeoutSeconds);

        // 3. 更新活跃时间
        entity.touch();

        return result;
    }

    @Override
    public void resizeTerminal(String sessionId, int cols, int rows) {
        log.debug("调整终端大小 sessionId={} cols={} rows={}", sessionId, cols, rows);

        TerminalSessionEntity entity = sessionCache.get(sessionId);
        if (entity == null || !entity.isActive()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭");
        }

        terminalSessionService.resize(sessionId, cols, rows);

        entity.setCols(cols);
        entity.setRows(rows);
        entity.touch();
    }

    @Override
    public TerminalSessionEntity getTerminalSession(String sessionId) {
        return sessionCache.get(sessionId);
    }

    @Override
    public void closeTerminal(String sessionId) {
        log.info("关闭终端会话 sessionId={}", sessionId);

        TerminalSessionEntity entity = sessionCache.remove(sessionId);
        if (entity != null) {
            terminalSessionService.closeSession(sessionId);
            log.info("终端会话已关闭 sessionId={}", sessionId);
        }
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return sessionCache.containsKey(sessionId);
    }

    @Override
    public String readTerminal(String sessionId) {
        TerminalSessionEntity entity = sessionCache.get(sessionId);
        if (entity == null || !entity.isActive()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭");
        }
        return terminalSessionService.read(sessionId);
    }

    @Override
    public void writeTerminal(String sessionId, String input) {
        TerminalSessionEntity entity = sessionCache.get(sessionId);
        if (entity == null || !entity.isActive()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭");
        }
        terminalSessionService.write(sessionId, input);
        entity.touch();
    }

}