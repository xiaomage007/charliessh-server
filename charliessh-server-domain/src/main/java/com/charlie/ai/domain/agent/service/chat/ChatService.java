package com.charlie.ai.domain.agent.service.chat;

import com.charlie.ai.domain.agent.model.entity.ChatCommandEntity;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import com.charlie.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import com.charlie.ai.domain.agent.service.IChatService;
import com.charlie.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.charlie.ai.types.enums.ResponseCode;
import com.charlie.ai.types.exception.AppException;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description: 对话服务实现类。标注 @Service 使其被 Spring 组件扫描注册为 Bean，
 * 这样调用方（含测试类）才能通过 IChatService 接口注入获得实例，否则启动注入阶段直接报 NoSuchBeanDefinitionException。
 * @author: Charlie
 * @date: 2026/10/14 9:37
 */
@Slf4j
@Service
public class ChatService implements IChatService {

    @jakarta.annotation.Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();
        tables.forEach((agentId, aiAgentConfigTableVO) -> {
            agentList.add(aiAgentConfigTableVO.getAgent());
        });
        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();
        String sessionKey = buildSessionKey(agentId, userId);
        return userSessions.computeIfAbsent(sessionKey, key -> {
            Session session = runner.sessionService().createSession(appName, userId)
                    .blockingGet();
            return session.id();
        });
    }

    private String buildSessionKey(String agentId, String userId) {
        return agentId + ":" + userId;
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String sessionId = createSession(agentId, userId);

        return handleMessage(agentId, userId, sessionId, message);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content userMsg = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content userMsg = Content.fromParts(Part.fromText(message));

        // 未绑定终端会话：显式清理一次，避免读到上一次请求残留的会话 ID
        return Flowable.defer(() -> {
                    SshExecuteAdkTool.clearCurrentTerminalSession();
                    return runner.runAsync(userId, sessionId, userMsg);
                })
                .doFinally(() -> SshExecuteAdkTool.clearCurrentTerminalSession());
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message, String terminalSessionId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content userMsg = Content.fromParts(Part.fromText(message));

        /*
         * 终端会话 ID 通过 ThreadLocal 传给 executeCommand 工具。
         *
         * 【为什么用 defer 而不是直接 set】
         * ADK 的 runAsync 返回的是 cold Flowable，真正的事件流在【订阅时】才开始。
         * 如果在方法返回前就 set，而订阅发生在另一个线程上，
         * 工具执行时就读不到这个值（InheritableThreadLocal 只在线程创建时继承一次）。
         * 放进 defer 里 set，保证 set 与工具执行发生在同一个订阅线程上。
         *
         * 【为什么必须清理】
         * Web 容器 / 线程池会复用线程。不清理的话，下一个请求会读到上一个请求残留的
         * terminalSessionId，导致命令被发到【别人的服务器】上执行——这是安全问题。
         * doFinally 覆盖正常结束、异常、取消三种收尾路径。
         */
        return Flowable.defer(() -> {
                    if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
                        log.info("设置终端会话ID: {}", terminalSessionId);
                        SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
                    } else {
                        SshExecuteAdkTool.clearCurrentTerminalSession();
                    }
                    return runner.runAsync(userId, sessionId, userMsg);
                })
                .doFinally(() -> SshExecuteAdkTool.clearCurrentTerminalSession());
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(chatCommandEntity.getAgentId());

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        texts.forEach((text) -> {
            parts.add(Part.fromText(text.getMessage()));
        });

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        files.forEach((file) -> {
            parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
        });

        List<ChatCommandEntity.Content.InlineData> inlineDatas = chatCommandEntity.getInlineDatas();
        inlineDatas.forEach((inlineData) -> {
            parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
        });

        Content content = Content.builder().role("user").parts(parts).build();

        // 获取运行体
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

}
