package com.charlie.ai.domain.ssh.service;


import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;
import com.charlie.ai.domain.ssh.model.entity.TerminalSessionEntity;

/**
 * SSH终端领域服务接口
 * 定义终端会话的核心业务操作
 */
public interface ISshTerminalService {

    /**
     * 打开终端会话
     *
     * @param connectionId SSH连接ID
     * @param cols          终端列数
     * @param rows          终端行数
     * @return 终端会话实体
     */
    TerminalSessionEntity openTerminal(String connectionId, int cols, int rows);

    /**
     * 执行命令并返回输出
     * <p>
     * 走 exec 通道（非交互式）：命令真正执行完才返回，输出与命令严格对应，
     * 并携带真实退出码。
     *
     * @param sessionId 会话ID
     * @param command    命令内容
     * @return 命令执行后的终端输出
     */
    String executeCommand(String sessionId, String command);

    /**
     * 执行命令并返回完整结果（含 stdout / stderr / 退出码）
     *
     * @param sessionId      会话ID
     * @param command        命令内容
     * @param timeoutSeconds 执行超时（秒）
     * @return 执行结果
     */
    TerminalExecResult executeCommand(String sessionId, String command, int timeoutSeconds);

    /**
     * 调整终端大小
     *
     * @param sessionId 会话ID
     * @param cols       新的列数
     * @param rows       新的行数
     */
    void resizeTerminal(String sessionId, int cols, int rows);

    /**
     * 获取终端会话
     *
     * @param sessionId 会话ID
     * @return 终端会话实体
     */
    TerminalSessionEntity getTerminalSession(String sessionId);

    /**
     * 关闭终端会话
     *
     * @param sessionId 会话ID
     */
    void closeTerminal(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean sessionExists(String sessionId);

    /**
     * 读取终端当前输出（不执行命令，用于同步状态）
     *
     * @param sessionId 会话ID
     * @return 当前终端输出
     */
    String readTerminal(String sessionId);

    /**
     * 写入原始输入到终端（逐字节模式，由 Shell 自身处理 echo）
     *
     * @param sessionId 会话ID
     * @param input     原始输入数据
     */
    void writeTerminal(String sessionId, String input);

}