package com.charlie.ai.test.trigger;

import com.charlie.ai.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionEntity;
import com.charlie.ai.domain.ssh.model.entity.TerminalExecResult;
import com.charlie.ai.domain.ssh.model.entity.TerminalSessionEntity;
import com.charlie.ai.domain.ssh.model.valobj.AuthTypeEnum;
import com.charlie.ai.domain.ssh.service.ISshConnectionService;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * SSH exec 通道语义验证测试
 * <p>
 * 这组用例锁定的都是 <b>交互式 shell 通道做不到、而 AI 场景必须正确</b> 的语义：
 * <ol>
 *     <li>命令真的被执行了（而不是只被终端回显）；</li>
 *     <li>输出与命令<b>严格一一对应</b>——第 N 条命令绝不会读到第 N-1 条的残留
 *         （这正是修复前「输出滞后一拍」的根因）；</li>
 *     <li>退出码可信，可以据此判断成功与否；</li>
 *     <li>stderr 不丢失；</li>
 *     <li>长命令不会把调用方永久挂死。</li>
 * </ol>
 *
 * @Author: Charlie
 * @Date: 2026/11/01
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshExecChannelTest {

    private static final Properties SSH_CONFIG = loadSshConfig();
    private static final String SSH_HOST = SSH_CONFIG.getProperty("ssh.host");
    private static final int SSH_PORT = Integer.parseInt(SSH_CONFIG.getProperty("ssh.port", "22"));
    private static final String SSH_USERNAME = SSH_CONFIG.getProperty("ssh.username");
    private static final String SSH_PASSWORD = SSH_CONFIG.getProperty("ssh.password");

    @Resource
    private ISshConnectionService sshConnectionService;

    @Resource
    private ISshTerminalService sshTerminalService;

    private static String connectionId;
    private static String terminalSessionId;
    private static boolean initialized;

    /**
     * 类级别清理用的服务引用（@AfterClass 是静态方法，拿不到实例字段）
     */
    private static ISshConnectionService cleanupConnectionService;
    private static ISshTerminalService cleanupTerminalService;

    /**
     * 从 classpath:ssh-config.local 读取 SSH 连接配置
     * <p>
     * 该文件不提交到 Git（已在 .gitignore 中排除），使用前请从 ssh-config.local_bak 复制并填写真实信息。
     */
    private static Properties loadSshConfig() {
        Properties props = new Properties();
        try (InputStream is = SshAgentReActTest.class.getClassLoader()
                                                     .getResourceAsStream("ssh-config.local")) {
            if (is == null) {
                throw new RuntimeException(
                        "未找到 ssh-config.local 文件！请复制 src/test/resources/ssh-config.local_bak 为 ssh-config.local 并填写服务器信息。");
            }
            props.load(is);
        } catch (Exception e) {
            throw new RuntimeException("加载 ssh-config.local 失败: " + e.getMessage(), e);
        }
        return props;
    }

    @Before
    public void init() {
        initOnce();
    }

    private synchronized void initOnce() {
        if (initialized) {
            return;
        }
        SshConnectionEntity connEntity = SshConnectionEntity.builder()
                .connectionName("exec通道测试")
                .host(SSH_HOST)
                .port(SSH_PORT)
                .username(SSH_USERNAME)
                .authType(AuthTypeEnum.PASSWORD)
                .password(SSH_PASSWORD)
                .userId("exec-test")
                .build();

        SshConnectionConfigEntity configEntity = SshConnectionConfigEntity.builder()
                .connectTimeout(15)
                .keepaliveInterval(30)
                .build();

        sshConnectionService.createConnection(connEntity, configEntity);
        connectionId = connEntity.getConnectionId();

        if (!sshConnectionService.connect(connectionId)) {
            throw new RuntimeException("SSH 连接失败，请检查 host/port/账号/密码");
        }

        TerminalSessionEntity terminal = sshTerminalService.openTerminal(connectionId, 120, 24);
        terminalSessionId = terminal.getSessionId();
        cleanupConnectionService = sshConnectionService;
        cleanupTerminalService = sshTerminalService;
        initialized = true;
        log.info("exec 通道测试会话就绪 terminalSessionId={}", terminalSessionId);
    }

    /**
     * 命令真的执行了：stdout 里有命令的真实输出
     */
    @Test
    public void test_execCommandReallyRuns() {
        TerminalExecResult result = sshTerminalService.executeCommand(
                terminalSessionId, "echo hello-exec-world", 30);

        log.info("stdout={} stderr={} exitCode={}", result.getStdout(), result.getStderr(),
                 result.getExitCode());

        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertFalse(result.isTimeout());
        assertTrue("stdout 应包含命令输出，实际: " + result.getStdout(),
                   result.getStdout().contains("hello-exec-world"));
    }

    /**
     * 【核心回归】输出与命令严格对应，不会串到上一条命令
     * <p>
     * 修复前：write 不带 \n 导致命令根本没执行，read 又是非阻塞快照，
     * 于是第 N 次调用拿到的永远是第 N-1 条命令的回显——模型据此判定失败并无限重试。
     */
    @Test
    public void test_outputBelongsToItsOwnCommand() {
        TerminalExecResult first = sshTerminalService.executeCommand(
                terminalSessionId, "echo first-cmd-marker", 30);
        TerminalExecResult second = sshTerminalService.executeCommand(
                terminalSessionId, "echo second-cmd-marker", 30);

        assertTrue(first.getStdout().contains("first-cmd-marker"));
        assertFalse("第一条命令不应包含第二条的输出，实际: " + first.getStdout(),
                    first.getStdout().contains("second-cmd-marker"));

        assertTrue(second.getStdout().contains("second-cmd-marker"));
        assertFalse("第二条命令不应包含第一条的输出，实际: " + second.getStdout(),
                    second.getStdout().contains("first-cmd-marker"));
    }

    /**
     * exec 通道不回显命令本身，输出里 marker 只出现一次
     * <p>
     * shell 通道会把输入的命令原样打印出来，导致「命令里含 error 等关键词」被误判为失败。
     */
    @Test
    public void test_noCommandEchoInOutput() {
        String marker = "unique-marker-xyz";
        TerminalExecResult result = sshTerminalService.executeCommand(
                terminalSessionId, "echo " + marker, 30);

        assertEquals("输出里不应出现命令回显，marker 只该出现一次，实际: " + result.getStdout(),
                     1, countOccurrences(result.getStdout(), marker));
    }

    /**
     * 命令不存在 → 退出码 127，stderr 有内容
     */
    @Test
    public void test_commandNotFoundExitCode() {
        TerminalExecResult result = sshTerminalService.executeCommand(
                terminalSessionId, "command_that_does_not_exist_zzz", 30);

        log.info("stdout={} stderr={} exitCode={}", result.getStdout(), result.getStderr(),
                 result.getExitCode());

        assertEquals(127, result.getExitCode());
        assertFalse(result.isSuccess());
        assertFalse("stderr 不应为空", result.getStderr().isBlank());
    }

    /**
     * 命令执行失败 → 退出码非 0，stderr 被完整捕获
     */
    @Test
    public void test_stderrCapturedOnFailure() {
        TerminalExecResult result = sshTerminalService.executeCommand(
                terminalSessionId, "cat /nonexistent_file_zzz_123", 30);

        log.info("stdout={} stderr={} exitCode={}", result.getStdout(), result.getStderr(),
                 result.getExitCode());

        assertNotEquals(0, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue("stderr 应包含错误描述，实际: " + result.getStderr(),
                   result.getStderr().contains("No such file"));
    }

    /**
     * 超时保护：命令跑不完时按时返回，而不是把调用方永久挂死
     */
    @Test
    public void test_timeoutBreaksLongCommand() {
        long start = System.currentTimeMillis();
        TerminalExecResult result = sshTerminalService.executeCommand(terminalSessionId, "sleep 30",
                                                                      3);
        long cost = System.currentTimeMillis() - start;

        log.info("timeout={} duration={}ms cost={}ms", result.isTimeout(), result.getDurationMs(),
                 cost);

        assertTrue("应标记为超时", result.isTimeout());
        assertFalse("超时不应算成功", result.isSuccess());
        assertTrue("应在超时时间附近返回，实际耗时 " + cost + "ms", cost < 15000);
    }

    /**
     * 超时之后连接仍然可用（超时只断开通道，不断开 SSH 会话）
     */
    @Test
    public void test_sessionUsableAfterTimeout() {
        sshTerminalService.executeCommand(terminalSessionId, "sleep 30", 2);

        TerminalExecResult result = sshTerminalService.executeCommand(
                terminalSessionId, "echo still-alive", 30);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("still-alive"));
    }

    /**
     * 统计子串出现次数
     */
    private static int countOccurrences(String text, String target) {
        if (text == null || target == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = text.indexOf(target);
        while (index >= 0) {
            count++;
            index = text.indexOf(target, index + target.length());
        }
        return count;
    }

    @AfterClass
    public static void tearDown() {
        if (!initialized) {
            return;
        }
        try {
            if (terminalSessionId != null && cleanupTerminalService != null) {
                cleanupTerminalService.closeTerminal(terminalSessionId);
            }
            if (connectionId != null && cleanupConnectionService != null) {
                cleanupConnectionService.disconnect(connectionId);
            }
        } catch (Exception e) {
            log.warn("清理 SSH 资源失败", e);
        } finally {
            initialized = false;
        }
    }

}
