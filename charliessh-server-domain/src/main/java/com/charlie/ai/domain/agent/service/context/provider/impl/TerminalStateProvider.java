package com.charlie.ai.domain.agent.service.context.provider.impl;

import com.charlie.ai.domain.agent.service.context.provider.IContextProvider;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 终端状态上下文提供者（order=10，最先执行）
 *
 * <p>
 * 功能：通过 SSH 终端实时采集远程服务器的环境信息（OS/用户/工作目录/运行时长），
 * 让模型"知道自己在哪台机器上操作"。该逻辑从 PromptService 下沉至此。
 * <p>
 * 运行过程：
 * <pre>
 *   provide(sessionId, userId, terminalSessionId, history)
 *        |
 *        | terminalSessionId 为空 ? --> 返回空 Map（无终端则不采集）
 *        v
 *   safeExec(terminalSessionId, cmd)  逐条执行采集命令
 *        |
 *        +-- "uname -srm"  --> osInfo          （操作系统/架构）
 *        +-- "whoami"      --> currentUser     （当前登录用户）
 *        +-- "pwd"         --> currentDirectory（当前工作目录）
 *        +-- "uptime"      --> uptime          （运行时长）
 *        |
 *        v
 *   Map{osInfo, currentUser, currentDirectory, uptime}
 *        |
 *        v
 *   ChatContextService 合并 --> PromptContextVO --> 消息前缀 [系统环境]
 * </pre>
 * 容错设计：每条命令独立 try-catch（safeExec），单条失败仅该字段留空，
 * 环境采集是"锦上添花"，绝不阻断主流程。
 * @Author: Charlie
 * @Date: 2026/11/16 11:11
 */
@Order(10)
@Component
public class TerminalStateProvider implements IContextProvider {

    @Resource
    private ISshTerminalService sshTerminalService;

    @Override
    public String getName() {
        return "terminal-state";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Map<String, Object> provide(String sessionId, String userId, String terminalSessionId,
                                       List<Map<String, Object>> messageHistory) {

        Map<String, Object> result = new HashMap<>();

        if (terminalSessionId == null || terminalSessionId.isEmpty()) {
            return result;
        }

        String osInfo = safeExec(terminalSessionId, "uname -srm");
        String user = safeExec(terminalSessionId, "whoami");
        String pwd = safeExec(terminalSessionId, "pwd");
        String uptime = safeExec(terminalSessionId, "uptime -p 2>/dev/null || uptime");

        result.put("osInfo", osInfo);
        result.put("currentUser", user);
        result.put("currentDirectory", pwd);
        result.put("uptime", uptime);
        return result;
    }

    private String safeExec(String terminalSessionId, String cmd) {
        try {
            String res = sshTerminalService.executeCommand(terminalSessionId, cmd);
            return res != null ? res.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

}