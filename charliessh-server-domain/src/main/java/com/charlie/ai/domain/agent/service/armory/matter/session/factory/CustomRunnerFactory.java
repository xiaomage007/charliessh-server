package com.charlie.ai.domain.agent.service.armory.matter.session.factory;

import com.charlie.ai.domain.agent.service.armory.matter.session.CustomAdkMemoryService;
import com.charlie.ai.domain.agent.service.armory.matter.session.CustomAdkSessionService;
import com.google.adk.agents.BaseAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.Runner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 接管 Runner 装配入口
 *
 * @author Charlie
 * @since 2026/11/19
 */
@Component
public class CustomRunnerFactory {

    @Resource
    private CustomAdkSessionService customAdkSessionService;
    @Resource
    private CustomAdkMemoryService customAdkMemoryService;

    public Runner create(BaseAgent baseAgent, String appName, List<BasePlugin> plugins) {
        return Runner.builder()
                     .agent(baseAgent)
                     .appName(appName)
                     .artifactService(new InMemoryArtifactService())
                     .sessionService(customAdkSessionService)
                     .memoryService(customAdkMemoryService)
                     .plugins(plugins)
                     .build();
    }

}