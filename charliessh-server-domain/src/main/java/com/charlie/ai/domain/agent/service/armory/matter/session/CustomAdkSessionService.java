package com.charlie.ai.domain.agent.service.armory.matter.session;

import com.google.adk.sessions.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

/**
 *
 *
 * @author Charlie
 * @since 2026/11/19
 */
public class CustomAdkSessionService implements BaseSessionService {



    @Override
    public Single<Session> createSession(String appName, String userId,
            @Nullable ConcurrentMap<String, Object> state, @Nullable String sessionId) {
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
}