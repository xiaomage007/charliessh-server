package com.charlie.ai.domain.agent.service.armory.matter.plugin;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/19 9:13
 */
@Slf4j
@Service("myTestPlugin")
public class MyTestPlugin extends BasePlugin {

    public MyTestPlugin(String name) {
        super(name);
    }

    public MyTestPlugin() {
        super("MyTestPlugin");
    }

    @Override
    public Maybe<Content> onUserMessageCallback(InvocationContext invocationContext, Content userMessage) {
        log.info("用户输入信息:{}", userMessage.text());
        return super.onUserMessageCallback(invocationContext, userMessage);
    }

    @Override
    public Maybe<Content> beforeAgentCallback(BaseAgent agent, CallbackContext callbackContext) {
        String agentName = agent.name();
        log.info("智能体名称:{}", agentName);
        return super.beforeAgentCallback(agent, callbackContext);
    }

    /**
     * 模型调用前的拦截回调。
     * <p>
     * 第二个参数之所以是 LlmRequest.Builder 而不是 LlmRequest，是 ADK Plugin 接口的约定：
     * 回调拿到的是「尚未发给模型的请求构建器」，插件可以直接在它上面改写请求内容
     * （例如切换模型、追加 system 指令、挂载工具），改完的内容会真正作用于本次推理。
     * 如果这里声明成 LlmRequest，就与接口签名不匹配，@Override 会报「方法不会覆盖或实现超类型的方法」。
     * <p>
     * 另外 Builder 上只提供 model(String) 这样的 setter，没有 model() getter，
     * 因此想读取当前模型名必须先 build() 成不可变对象再取。
     */
    @Override
    public Maybe<LlmResponse> beforeModelCallback(CallbackContext callbackContext, LlmRequest.Builder llmRequest) {
        Optional<String> model = llmRequest.build().model();
        log.info("ai 模型:{}", model.orElse(""));
        return super.beforeModelCallback(callbackContext, llmRequest);
    }
}
