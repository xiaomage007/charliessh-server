package com.charlie.ai.domain.agent.service.armory.matter.session;

import com.charlie.ai.domain.agent.service.armory.matter.session.model.SessionSnapshot;
import com.google.adk.events.Event;
import com.google.adk.sessions.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自定义 ADK Session 服务。
 *
 * <p>这个类并不是去“1:1 复刻” ADK 原生/默认 Session 的完整存储语义，
 * 而是围绕当前 ReAct + SSH 场景，做了一层<strong>面向运行期的轻量化会话治理</strong>。
 *
 * <p>为什么要自己接管 Session？
 * 因为当前工程在业务层已经做了动态 Prompt 上下文增强，例如：
 * <pre>
 *   [系统环境]
 *   [最近执行的命令]
 *   [关键事件]
 *   ---
 *   用户原始问题
 * </pre>
 *
 * <p>这类“富化消息”适合发给模型做推理，但并不适合再被 ADK 原样存回 Session。
 * 如果直接回灌，会带来几个典型问题：
 * <pre>
 *   1. 用户原始问题被动态前缀污染
 *   2. 相同上下文在业务层和框架层重复保存
 *   3. tool / assistant 长文本让 Session 持续膨胀
 *   4. 对话轮次越来越长，后续取历史成本越来越高
 * </pre>
 *
 * <p>所以，这个类相对“原生 ADK Session 使用方式”，做了几项有意识的简化：
 * <pre>
 *   1. 只保留运行期轻量 Session，不做持久化落库
 *   2. 不追求保存完整原文历史，而是保存“够用”的净化后历史
 *   3. 不把动态 Prompt 前缀原样写回 Session，避免上下文重复
 *   4. 不保留无限长的 tool/assistant 文本，而是按类型截断
 *   5. 不保留无限轮对话，而是只保留最近 MAX_TURNS 轮、最多 MAX_EVENTS 条
 *   6. 只同步 event 中真正有价值的 stateDelta，维持最新运行态 state
 * </pre>
 *
 * <p>你可以把它理解成：放在 ADK Session 前面的一道“净化器 + 限流器 + 修剪器”。
 * 目标不是把所有历史都留下，而是让框架层会话<strong>干净、轻量、可控</strong>，
 * 同时避免与业务侧 {@code ChatContextService} 管理的上下文发生重复和打架。
 *
 * @author Charlie
 * @since 2026/11/19
 */
@Component
public class CustomAdkSessionService implements BaseSessionService {

    /**
     * appName -> userId -> sessionId -> SessionSnapshot
     */
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, SessionSnapshot>>> sessions = new ConcurrentHashMap<>();
    /**
     * 预留的 user 级别状态存储。
     */
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Object>>> userState = new ConcurrentHashMap<>();
    /**
     * 预留的 app 级别状态存储。
     */
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> appState = new ConcurrentHashMap<>();

    /**
     * 创建一个新的轻量 Session 快照。
     *
     * <p>这里的设计很克制：
     * 只初始化当前会话运行必需的 state、event 容器和更新时间，
     * 不做更重的持久化、归档、索引等动作。
     *
     * <p>案例：
     * <pre>
     *   appName   = walissh-app
     *   userId    = user-001
     *   sessionId = react-session-01
     *
     *   创建后得到：
     *   sessions["walissh-app"]["user-001"]["react-session-01"] = SessionSnapshot
     * </pre>
     *
     * @param appName      应用名
     * @param userId       用户 ID
     * @param initialState 初始状态
     * @param sessionId    会话 ID，若为空则自动生成
     * @return 对外暴露的 ADK Session
     */
    @Override
    public Single<Session> createSession(String appName, String userId,
                                         @Nullable ConcurrentMap<String, Object> initialState, @Nullable String sessionId) {
        String finalSessionId = Optional.ofNullable(sessionId)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> UUID.randomUUID().toString());
        State state = new State(initialState == null ? new ConcurrentHashMap<>() : initialState);

        SessionSnapshot sessionSnapshot = SessionSnapshot.builder()
                .sessionKey(new SessionKey(appName, userId, finalSessionId))
                .state(state)
                .rawEvents(new CopyOnWriteArrayList<>())
                .lastUpdateTime(Instant.now())
                .build();

        sessions.computeIfAbsent(appName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(finalSessionId, sessionSnapshot);

        userState.computeIfAbsent(appName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        appState.computeIfAbsent(appName, k -> new ConcurrentHashMap<>());

        return null;
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId, String sessionId,
                                     Optional<GetSessionConfig> config) {
        return null;
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return null;
    }

    @Override
    public Completable deleteSession(String appName, String userId, String sessionId) {
        return null;
    }

    @Override
    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
        return null;
    }

    /**
     * 将内部快照对象转换为对外暴露的 ADK Session。
     *
     * <p>内部真实存储用的是 {@link SessionSnapshot}，
     * 对外仍然按 ADK 的 Session 语义返回，方便 Runner 正常使用。
     *
     * @param snapshot       内部快照
     * @param eventsOverride 可选的事件覆盖集
     * @return ADK Session
     */
    private Session toSession(SessionSnapshot snapshot, Optional<List<Event>> eventsOverride) {
        List<Event> events = eventsOverride.orElseGet(snapshot::getRawEvents);
        Session session = Session.builder(snapshot.getSessionKey())
                .state(snapshot.getState())
                .events(events)
                .build();
        session.lastUpdateTime(snapshot.getLastUpdateTime());
        return session;
    }
}