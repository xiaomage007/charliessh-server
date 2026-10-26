package com.charlie.ai.config;

import com.alibaba.fastjson.JSON;
import com.charlie.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import com.charlie.ai.domain.agent.service.IArmoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.ArrayList;

/**
 * Ai Agent 智能体自动装配配置类。
 *
 * <p>职责说明：
 * <ol>
 *   <li>通过 {@link EnableConfigurationProperties} 把 application.yml 中
 *       {@code ai.agent.config} 前缀下的配置（是否启用 enabled、智能体配置表 tables）
 *       绑定为 Spring 容器中的 Bean（{@link AiAgentAutoConfigProperties}），
 *       供项目中任何位置注入使用；</li>
 *   <li>实现 {@link ApplicationListener} 监听 {@link ApplicationReadyEvent}，
 *       在 Spring 容器完全启动就绪后执行智能体装配相关的初始化动作，
 *       保证初始化时依赖的其它 Bean 均已创建完毕。</li>
 * </ol>
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * ai:
 *   agent:
 *     config:
 *       enabled: true
 *       tables:
 *         agent01:                # 配置表标识，自定义命名
 *           appName: charliessh-server
 *           agent:
 *             agentId: 1
 *             agentName: 智能体01
 *           module:
 *             aiApi: {baseUrl: xxx, apiKey: xxx}
 *             chatModel: {model: xxx}
 *             agents: [...]
 *             agentWorkflows: [...]
 * </pre>
 *
 * @author: Charlie
 * @date: 2026/10/13 8:38
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
public class AiAgentAutoConfig implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * Ai Agent 自动装配配置属性，封装 application.yml 中 {@code ai.agent.config} 下的全部配置。
     *
     * <p>字段说明：
     * <ul>
     *   <li>{@code enabled}：是否启用智能体自动装配，默认 false，即不配置就不装配；</li>
     *   <li>{@code tables}：智能体配置表集合，key 为配置表标识（自定义命名，如 agent01），
     *       value 为该配置表的具体内容（appName、agent、module 等）。</li>
     * </ul>
     *
     * <p>该对象由 {@code @EnableConfigurationProperties(AiAgentAutoConfigProperties.class)}
     * 注册为 Spring Bean，这里通过字段注入获取；与构造器注入相比，字段注入可以避免
     * 配置类与配置属性 Bean 之间的初始化顺序耦合。
     */
    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IArmoryService armoryService;

    /**
     * Spring 容器启动就绪后的回调，用于执行智能体装配的初始化逻辑。
     *
     * <p>为什么选择 {@link ApplicationReadyEvent} 而不是 ContextRefreshedEvent：
     * ApplicationReadyEvent 在 {@code SpringApplication.run()} 的最后、
     * 所有 ApplicationRunner / CommandLineRunner 执行<b>之前</b>发布，
     * 此时 Web 服务器已启动、容器完全就绪，可以安全地执行依赖整个上下文
     * 及外部资源（HTTP 客户端、数据库等）的初始化。
     *
     * <p>执行流程：
     * <ol>
     *   <li>读取 {@code ai.agent.config.tables} 下所有配置表的值集合；</li>
     *   <li>通过 fastjson 序列化为 JSON 字符串输出到日志，便于启动阶段
     *       直观核对配置是否被正确加载（当前为占位逻辑，后续将替换为
     *       真正的智能体装配动作）；</li>
     *   <li>初始化过程一旦出现异常，直接包装为 RuntimeException 抛出，
     *       遵循 fail-fast 原则，避免带着错误的配置继续对外提供服务。</li>
     * </ol>
     *
     * @param event Spring 容器就绪事件，可从上下文进一步获取 Environment、
     *              ApplicationContext 等信息（当前未使用）
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            // values() 取出 Map 中所有配置表；key 为配置表标识（自定义命名），此处仅需值集合
            log.info("Ai Agent 智能体装配 {}", JSON.toJSONString(aiAgentAutoConfigProperties.getTables().values()));

            armoryService.acceptArmoryAgents(new ArrayList<>(aiAgentAutoConfigProperties.getTables().values()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
