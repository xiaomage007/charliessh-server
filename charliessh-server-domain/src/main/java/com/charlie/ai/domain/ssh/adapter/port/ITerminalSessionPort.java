package com.charlie.ai.domain.ssh.adapter.port;

import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;

/**
 * 终端会话服务接口
 * 负责管理 SSH 终端会话，包括打开/写入/读取/调整大小/关闭会话
 */
public interface ITerminalSessionPort {

    /**
     * 打开终端会话
     *
     * @param connectionId SSH连接ID
     * @param cols         终端列数
     * @param rows         终端行数
     * @return 会话ID
     */
    String openTerminal(String connectionId, int cols, int rows);

    /**
     * 写入命令到终端
     *
     * @param sessionId 会话ID
     * @param command   命令内容
     */
    void write(String sessionId, String command);

    /**
     * 非交互式执行一条命令并等待其结束（exec 通道）
     * <p>
     * 与交互式 shell 通道的本质区别：
     * <ul>
     *     <li>命令一次性执行完即返回，<b>没有回显、没有提示符</b>；</li>
     *     <li>stdout / stderr 分离，服务端回传<b>真实退出码</b>；</li>
     *     <li>输出与命令<b>严格一一对应</b>，不存在"读到上一条命令"的错位。</li>
     * </ul>
     * 适用于 AI Agent 这类"执行一条命令 → 拿到结果 → 再决定下一步"的场景。
     *
     * @param connectionId   SSH 连接 ID
     * @param command        要执行的命令
     * @param timeoutSeconds 执行超时（秒）
     * @return 执行结果（含 stdout / stderr / 退出码）
     */
    TerminalExecResult execCommand(String connectionId, String command, int timeoutSeconds);

    /**
     * 读取终端输出
     *
     * @param sessionId 会话ID
     * @return 终端输出内容
     */
    String read(String sessionId);

    /**
     * 调整终端大小
     *
     * @param sessionId 会话ID
     * @param cols      新的列数
     * @param rows      新的行数
     */
    void resize(String sessionId, int cols, int rows);

    /**
     * 关闭终端会话
     *
     * @param sessionId 会话ID
     */
    void closeSession(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean sessionExists(String sessionId);

}