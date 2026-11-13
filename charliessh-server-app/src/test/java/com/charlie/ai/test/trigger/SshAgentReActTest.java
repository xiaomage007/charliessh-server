package com.charlie.ai.test.trigger;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.InputStream;
import java.util.Properties;

/**
 * @Description: SSH Agent ReAct 真实链路测试
 *
 * <p>
 * 与 SshAgentMvpTest 类连接 SSH 服务器，但测试 ReAct 执行模型。
 * <p>
 * ReAct 模式：AI 思考 → 调用工具（executeCommand）→ 分析结果 → 继续思考 → ...
 * 直至给出最终回答。
 * <p>
 * 使用前请修改下方 SSH_SERVER 配置为你自己的服务器信息。
 * @Author: Charlie
 * @Date: 2026/11/13 14:12
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshAgentReActTest {

    // ==================== 配置区：从 ssh-config.local 读取 ==================== https://618.gaga.plus
    // 使用方法：复制 src/test/resources/ssh-config.local_bak 为 ssh-config.local，填入你的服务器信息
    private static final Properties SSH_CONFIG = loadSshConfig();
    private static final String SSH_HOST = SSH_CONFIG.getProperty("ssh.host");
    private static final int SSH_PORT = Integer.parseInt(SSH_CONFIG.getProperty("ssh.port", "22"));
    private static final String SSH_USERNAME = SSH_CONFIG.getProperty("ssh.username");
    private static final String SSH_PASSWORD = SSH_CONFIG.getProperty("ssh.password");

    // Agent 配置（对应 only-one-agent.yml 中的 agent-id）
    private static final String AGENT_ID = "100000";
    private static final String USER_ID = "react-Charlie";
    // =================================================================

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




}