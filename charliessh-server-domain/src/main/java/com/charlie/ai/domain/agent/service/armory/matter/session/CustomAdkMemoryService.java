package com.charlie.ai.domain.agent.service.armory.matter.session;

import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.SearchMemoryResponse;
import com.google.adk.sessions.Session;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

/**
 * Memory 先空实现，把长期记忆能力留给后续专门设计
 * 如果 ADK Memory 再自动写一套长期记忆，就会出现两个问题：
 * 1)、边界不清：到底是业务层上下文说了算，还是 ADK Memory 说了算？
 * 2)、重复污染：动态 Prompt、工具结果、模型回答可能被 Memory 再次召回，形成隐式回灌。
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