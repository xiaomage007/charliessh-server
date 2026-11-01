package com.charlie.ai.test.trigger;

import com.charlie.ai.Application;
import com.charlie.ai.domain.agent.service.IChatService;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionEntity;
import com.charlie.ai.domain.ssh.model.entity.TerminalSessionEntity;
import com.charlie.ai.domain.ssh.model.valobj.AuthTypeEnum;
import com.charlie.ai.domain.ssh.service.ISshConnectionService;
import com.charlie.ai.domain.ssh.service.ISshTerminalService;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SSH Agent MVP 测试
 * <p>
 * 最小可用版本：启动后自动连接服务器，在控制台输入自然语言指令，
 * AI Agent 自动调用 executeCommand 工具执行 SSH 命令，并返回分析结果。
 * <p>
 * 使用前请修改下方 SSH_SERVER 配置为你自己的服务器信息。
 * <p>
 * ======================= 关于「在哪里输入」 =======================
 * 本类支持三种输入来源，按优先级依次生效：
 * <ol>
 *     <li><b>脚本模式</b>：-Dssh.agent.cmdFile=&lt;文件路径&gt;（或环境变量 SSH_AGENT_CMD_FILE）。
 *     文件每行一条指令，# 开头为注释。示例见 src/test/resources/ssh-agent-commands.txt。
 *     这种方式在 IDEA / mvn test / CI 下都能稳定跑，不会出现无输入可写的卡死。</li>
 *     <li><b>交互模式</b>：直接在控制台输入自然语言后回车，输入 exit 退出。
 *     注意：IDEA 的 JUnit 运行配置默认不提供可写的 stdin，
 *     请改用 <b>Application</b> 类型运行本类的 main 方法（见下方 main 注释），控制台即可输入。</li>
 *     <li><b>默认兜底</b>：stdin 不可用（EOF）或等待超时后，自动执行内置默认指令，
 *     保证测试不会一直挂着。</li>
 * </ol>
 * 相关参数：-Dssh.agent.mode=auto|interactive|script、-Dssh.agent.inputTimeoutSec=30
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshAgentMvpTest {

//    private static final String SSH_HOST = "更换为你的服务器IP 81.70.245.73";
    private static final String SSH_HOST = "115.159.150.103";
    private static final int SSH_PORT = 22;
    private static final String SSH_USERNAME = "ubuntu";
//    private static final String SSH_PASSWORD = "更换为你的服务器密码";
    private static final String SSH_PASSWORD = "mahui19871@@1";

    // Agent 配置（对应 ssh-agent.yml 中的 agent-id）
    private static final String AGENT_ID = "100000";
    private static final String USER_ID = "mvp";

    /** 运行模式：auto（默认）/ interactive / script */
    private static final String MODE_PROPERTY = "ssh.agent.mode";
    /** 指令文件路径：系统属性优先，其次环境变量 */
    private static final String CMD_FILE_PROPERTY = "ssh.agent.cmdFile";
    private static final String CMD_FILE_ENV = "SSH_AGENT_CMD_FILE";
    /** 交互模式等待一行的超时秒数，0 = 无限等待 */
    private static final int INPUT_TIMEOUT_SEC = Integer.getInteger("ssh.agent.inputTimeoutSec", 30);
    /** classpath 下内置的默认指令脚本 */
    private static final String DEFAULT_SCRIPT_RESOURCE = "ssh-agent-commands.txt";

    /** 内置默认指令（连 classpath 脚本都读不到时的最后兜底） */
    private static final List<String> BUILT_IN_COMMANDS = List.of(
            "查看服务器系统信息，包括操作系统版本、CPU、内存",
            "检查 docker 是否安装",
            "查看磁盘和内存使用情况"
    );

    @Resource
    private ISshConnectionService sshConnectionService;

    @Resource
    private ISshTerminalService sshTerminalService;

    @Resource
    private IChatService chatService;

    /** 整个 JVM 内只建立一次 SSH 连接，三个测试方法共用 */
    private static boolean initialized;
    /** 连接ID（createConnection 后自动生成） */
    private static String connectionId;
    /** 终端会话ID（openTerminal 后自动生成） */
    private static String terminalSessionId;
    /** 对话会话ID（createSession 后自动生成） */
    private static String chatSessionId;
    /** 用于类级别清理的服务引用 */
    private static ISshConnectionService cleanupConnectionService;
    private static ISshTerminalService cleanupTerminalService;

    /**
     * 初始化：创建连接 → 建立SSH → 打开终端 → 创建对话会话 → 绑定ThreadLocal
     */
    @Before
    public void init() {
        initOnce();
    }

    /**
     * 幂等初始化：只有第一次调用会真正建立 SSH 连接，
     * 避免同一个测试类里每个用例都重连一遍服务器。
     */
    private synchronized void initOnce() {
        if (initialized) {
            log.info("复用已建立的会话 connectionId={}, terminalSessionId={}, chatSessionId={}",
                    connectionId, terminalSessionId, chatSessionId);
            return;
        }

        log.info("========== MVP 初始化开始 ==========");

        // 1. 创建 SSH 连接记录
        SshConnectionEntity connEntity = SshConnectionEntity.builder()
                .connectionName("MVP测试连接")
                .host(SSH_HOST)
                .port(SSH_PORT)
                .username(SSH_USERNAME)
                .authType(AuthTypeEnum.PASSWORD)
                .password(SSH_PASSWORD)
                .userId(USER_ID)
                .build();

        SshConnectionConfigEntity configEntity = SshConnectionConfigEntity.builder()
                .connectTimeout(15)
                .keepaliveInterval(30)
                .build();

        sshConnectionService.createConnection(connEntity, configEntity);
        connectionId = connEntity.getConnectionId();
        log.info("1. 连接记录创建成功 connectionId={}", connectionId);

        // 2. 建立 SSH 连接
        boolean connected = sshConnectionService.connect(connectionId);
        if (!connected) {
            throw new RuntimeException("SSH 连接失败，请检查 host/port/账号/密码");
        }
        log.info("2. SSH 连接成功 host={}:{}", SSH_HOST, SSH_PORT);

        // 3. 打开终端会话
        TerminalSessionEntity terminal = sshTerminalService.openTerminal(connectionId, 120, 24);
        terminalSessionId = terminal.getSessionId();
        log.info("3. 终端会话已打开 terminalSessionId={}", terminalSessionId);

        // 4. 创建 AI 对话会话
        chatSessionId = chatService.createSession(AGENT_ID, USER_ID);
        log.info("4. AI 对话会话已创建 chatSessionId={}", chatSessionId);

        // 5. 绑定终端会话到 ThreadLocal（核心！executeCommand 工具从这里取 terminalSessionId）
        SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
        log.info("5. ThreadLocal 已绑定终端会话");

        cleanupConnectionService = sshConnectionService;
        cleanupTerminalService = sshTerminalService;
        initialized = true;

        log.info("========== MVP 初始化完成，可以开始对话了 ==========\n");
    }

    /**
     * 交互式对话：控制台输入自然语言，AI Agent 执行命令并返回结果
     * <p>
     * 运行后在控制台输入：
     * - "查看服务器系统信息"
     * - "检查 docker 是否安装"
     * - "查看磁盘和内存使用情况"
     * - "exit" 退出
     * <p>
     * 若 stdin 不可用（IDEA 的 JUnit 控制台就是这种情况），会自动降级为脚本模式执行内置指令，
     * 不会卡死在这一行。
     */
    @Test
    public void test_ai_shell() {
        runRepl(false);
    }

    /**
     * 以 Application 方式运行（推荐用于需要手动输入的场景）：
     * <p>
     * IDEA 中新建运行配置 → 类型选 Application → Main class 填
     * {@code com.charlie.ai.test.trigger.SshAgentMvpTest}，
     * 此时控制台支持直接键入，不会出现「没有输入的地方」。
     * <p>
     * 也可以在运行配置的 VM options 里加 {@code -Dssh.agent.cmdFile=<文件路径>} 走脚本模式。
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run(args);
        try {
            SshAgentMvpTest runner = new SshAgentMvpTest();
            runner.sshConnectionService = context.getBean(ISshConnectionService.class);
            runner.sshTerminalService = context.getBean(ISshTerminalService.class);
            runner.chatService = context.getBean(IChatService.class);
            runner.initOnce();
            runner.runRepl(true);
        } finally {
            cleanupStatic();
            context.close();
        }
    }

    /**
     * 单次对话测试：不交互，直接执行一条指令看结果
     */
    @Test
    public void test_singleChat() {
        SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);

        String message = "查看服务器系统信息，包括操作系统版本、CPU、内存";
        log.info("发送消息: {}", message);

        Flowable<Event> events = chatService.handleMessageStream(
                AGENT_ID, USER_ID, chatSessionId, message, terminalSessionId);

        System.out.print("\nAI > ");
        events.blockingForEach(event -> {
            String text = event.stringifyContent();
            if (!text.isEmpty()) {
                System.out.print(text);
            }
        });
        System.out.println();
    }

    /**
     * 直接测试 SSH 命令执行（不经过 AI，验证 SSH 链路是否通）
     */
    @Test
    public void test_directCommand() {
        log.info("直接执行 SSH 命令测试（不经过 AI）");

        String[] commands = {
                "whoami",
                "hostname",
                "uname -a",
                "df -h",
                "free -m"
        };

        for (String cmd : commands) {
            System.out.println("\n--- 执行: " + cmd + " ---");
            String output = sshTerminalService.executeCommand(terminalSessionId, cmd);
            System.out.println(output);
        }
    }

    /**
     * 关闭 SSH 连接与终端会话
     */
    @AfterClass
    public static void tearDown() {
        cleanupStatic();
    }

    private static void cleanupStatic() {
        if (!initialized) {
            return;
        }
        try {
            if (cleanupTerminalService != null && terminalSessionId != null) {
                cleanupTerminalService.closeTerminal(terminalSessionId);
            }
            if (cleanupConnectionService != null && connectionId != null) {
                cleanupConnectionService.disconnect(connectionId);
            }
        } catch (Exception e) {
            log.warn("清理 SSH 资源失败", e);
        } finally {
            initialized = false;
            SshExecuteAdkTool.clearCurrentTerminalSession();
        }
    }

    // ==================================================================================
    // 控制台交互实现
    // ==================================================================================

    /**
     * 执行一轮对话：按模式选择输入来源
     *
     * @param forceInteractive true 表示强制交互模式（Application 启动时使用，不做超时降级）
     */
    private void runRepl(boolean forceInteractive) {
        printBanner();

        String mode = forceInteractive ? "interactive" : System.getProperty(MODE_PROPERTY, "auto");

        if ("script".equalsIgnoreCase(mode)) {
            runScriptMode();
            return;
        }
        if ("auto".equalsIgnoreCase(mode) && resolveCommandFile() != null) {
            log.info("[模式] 检测到指令文件，自动切换为脚本模式");
            runScriptMode();
            return;
        }

        // interactive / auto：尝试从控制台读输入
        boolean completed = runInteractiveMode(forceInteractive ? 0 : INPUT_TIMEOUT_SEC);
        if (!completed) {
            log.warn("[模式] 标准输入不可用（EOF 或等待 {} 秒超时），自动降级为脚本模式", INPUT_TIMEOUT_SEC);
            runScriptMode();
        }
    }

    /**
     * 交互模式：控制台逐行读入自然语言指令
     *
     * @param timeoutSec 单行等待超时秒数，0 = 无限等待
     * @return true 正常结束（用户输入 exit / EOF）；false 表示降级
     */
    private boolean runInteractiveMode(int timeoutSec) {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\n你 > ");
                System.out.flush();

                String input = readLineWithTimeout(scanner, timeoutSec);
                if (input == null) {
                    // EOF 或超时
                    System.out.println();
                    return false;
                }

                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    System.out.println("再见！");
                    return true;
                }

                chatOnce(input);
            }
        }
    }

    /**
     * 脚本模式：按顺序执行一批指令
     */
    private void runScriptMode() {
        List<String> commands = readScriptCommands();
        log.info("[模式] 脚本模式，共 {} 条指令待执行", commands.size());
        for (String command : commands) {
            chatOnce(command);
        }
    }

    /**
     * 读取脚本指令：外部指令文件 > classpath 默认脚本 > 内置兜底列表
     */
    private List<String> readScriptCommands() {
        Path file = resolveCommandFile();
        if (file != null) {
            List<String> commands = readCommandsFromFile(file);
            if (!commands.isEmpty()) {
                log.info("[脚本] 从文件读取 {} 条指令: {}", commands.size(), file.toAbsolutePath());
                return commands;
            }
            log.warn("[脚本] 指令文件为空或不存在: {}", file.toAbsolutePath());
        }

        List<String> commands = readCommandsFromClasspath();
        if (!commands.isEmpty()) {
            log.info("[脚本] 从 classpath:{} 读取 {} 条指令", DEFAULT_SCRIPT_RESOURCE, commands.size());
            return commands;
        }

        log.info("[脚本] 使用内置默认指令 {} 条", BUILT_IN_COMMANDS.size());
        return new ArrayList<>(BUILT_IN_COMMANDS);
    }

    /**
     * 解析指令文件路径：系统属性 > 环境变量
     */
    private Path resolveCommandFile() {
        String path = System.getProperty(CMD_FILE_PROPERTY);
        if (path == null || path.isBlank()) {
            path = System.getenv(CMD_FILE_ENV);
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Paths.get(path.trim());
        } catch (Exception e) {
            log.warn("[脚本] 指令文件路径非法: {}", path, e);
            return null;
        }
    }

    /**
     * 读取指令文件：每行一条，# 开头为注释，空行忽略
     */
    private List<String> readCommandsFromFile(Path file) {
        List<String> commands = new ArrayList<>();
        if (file == null || !Files.exists(file)) {
            return commands;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                commands.add(trimmed);
            }
        } catch (IOException e) {
            log.warn("[脚本] 读取指令文件失败: {}", file, e);
        }
        return commands;
    }

    /**
     * 读取 classpath 下的默认指令脚本
     */
    private List<String> readCommandsFromClasspath() {
        List<String> commands = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DEFAULT_SCRIPT_RESOURCE)) {
            if (in == null) {
                return commands;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    commands.add(trimmed);
                }
            }
        } catch (IOException e) {
            log.warn("[脚本] 读取 classpath 脚本失败: {}", DEFAULT_SCRIPT_RESOURCE, e);
        }
        return commands;
    }

    /**
     * 带超时地读取一行控制台输入
     * <p>
     * Scanner 读取 System.in 是阻塞调用，若控制台没有可写通道（IDEA 的 JUnit 控制台、
     * surefire 派生的 JVM 等），会一直挂着。因此放到独立线程里读，并设置超时，
     * 超时或 EOF 都返回 null，由调用方降级到脚本模式。
     *
     * @param timeoutSec 超时秒数，0 = 无限等待
     * @return 读到的内容；null 表示 EOF 或超时
     */
    private String readLineWithTimeout(Scanner scanner, int timeoutSec) {
        if (timeoutSec <= 0) {
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ssh-agent-console-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<String> future = executor.submit(scanner::nextLine);
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[交互] 等待控制台输入超过 {} 秒，自动降级", timeoutSec);
            return null;
        } catch (ExecutionException e) {
            // EOF 时 Scanner.nextLine 抛 NoSuchElementException
            log.info("[交互] 标准输入已结束（EOF）: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 执行一次对话：把自然语言指令交给 Agent，打印流式输出
     */
    private void chatOnce(String input) {
        try {
            // 每次对话前重新绑定 ThreadLocal（防止异步线程丢失）
            SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);

            System.out.print("AI > ");
            Flowable<Event> events = chatService.handleMessageStream(
                    AGENT_ID, USER_ID, chatSessionId, input, terminalSessionId);

            StringBuilder fullContent = new StringBuilder();
            events.blockingForEach(event -> {
                String text = event.stringifyContent();
                if (text != null && !text.isEmpty()) {
                    System.out.print(text);
                    fullContent.append(text);
                }
            });
            System.out.println(); // 换行
        } catch (Exception e) {
            log.error("对话异常", e);
            System.out.println("出错: " + e.getMessage());
        }
    }

    /**
     * 打印启动说明，明确告诉用户「在哪里输入」
     */
    private void printBanner() {
        System.out.println("""

                ==================== SSH Agent 控制台 ====================
                输入方式（优先级从高到低）：
                  1) 脚本模式：-Dssh.agent.cmdFile=<文件路径>（或环境变量 SSH_AGENT_CMD_FILE）
                     每行一条指令，# 开头为注释；示例：src/test/resources/ssh-agent-commands.txt
                  2) 交互模式：在本控制台直接输入自然语言后回车，输入 exit 退出
                     提示：IDEA 的 JUnit 运行配置默认没有可写 stdin，
                           请改用 Application 类型运行本类的 main 方法，控制台即可输入
                  3) 兜底模式：stdin 不可用（EOF）或等待超时后，自动执行内置默认指令
                可调参数：-Dssh.agent.mode=auto|interactive|script、-Dssh.agent.inputTimeoutSec=30
                ===========================================================
                """);
    }
}
