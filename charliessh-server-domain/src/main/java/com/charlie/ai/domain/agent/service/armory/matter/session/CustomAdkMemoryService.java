package com.charlie.ai.domain.agent.service.armory.matter.session;

import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.SearchMemoryResponse;
import com.google.adk.sessions.Session;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author Charlie
 * @since 2026/11/19
 */
@Component
public class CustomAdkMemoryService implements BaseMemoryService {

    @Override
    public Completable addSessionToMemory(Session session) {
        return Completable.complete();
    }

    @Override
    public Single<SearchMemoryResponse> searchMemory(String appName, String userId, String query) {
        return Single.just(SearchMemoryResponse.builder()
                                               .memories(ImmutableList.of())
                                               .build());
    }

}