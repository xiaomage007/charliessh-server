package com.charlie.ai.domain.agent.service.armory.matter.session.model;

import com.google.adk.events.Event;
import com.google.adk.sessions.SessionKey;
import com.google.adk.sessions.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 内部会话快照模型
 *
 * @author Charlie
 * @since 2026/11/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSnapshot {

    private SessionKey sessionKey;
    private State state;
    private List<Event> rawEvents;
    private Instant lastUpdateTime;

}