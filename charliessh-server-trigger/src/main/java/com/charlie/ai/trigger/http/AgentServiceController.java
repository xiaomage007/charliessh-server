package com.charlie.ai.trigger.http;

import com.charlie.ai.api.IAgentService;
import com.charlie.ai.api.dto.*;
import com.charlie.ai.api.response.Response;
import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.charlie.ai.domain.agent.service.IChatService;
import com.charlie.ai.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.charlie.ai.types.enums.ResponseCode;
import com.charlie.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/20 19:00
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    @Resource
    private SshExecuteAdkTool sshExecuteAdkTool;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setContent(String.join("\n", messages));

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat_stream", method = RequestMethod.POST)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{} terminalSessionId:{} message:{}",
                    requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(),
                    requestDTO.getTerminalSessionId(), requestDTO.getMessage());

            // 如果未指定sessionId，先创建
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            // 创建 SSE 发射器（10 分钟超时，AI + SSH 命令执行可能需要较长时间）
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(10 * 60 * 1000L);

            // 绑定终端会话到 ThreadLocal（核心！工具从这里取 terminalSessionId）
            String terminalSessionId = requestDTO.getTerminalSessionId();
            if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
                SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
            }

            // 异步执行，避免阻塞 HTTP 线程
            String finalSessionId = sessionId;
            new Thread(() -> {
                // 心跳保活线程：在 AI 处理期间定期发送 SSE 注释行，防止浏览器/proxy 超时断开
                AtomicBoolean streaming = new AtomicBoolean(true);
                Thread heartbeatThread = new Thread(() -> {
                    try {
                        while (streaming.get()) {
                            Thread.sleep(15000);
                            try {
                                emitter.send(": heartbeat\n\n");
                            } catch (Exception e) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // 正常退出
                    }
                }, "sse-heartbeat-" + finalSessionId);
                heartbeatThread.setDaemon(true);
                heartbeatThread.start();

                try {

                } catch (Exception e) {
                    log.error("MVP 流式对话异常", e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("event", "error");
                        errorEvent.put("content", e.getMessage());
                        emitter.send(new ObjectMapper().writeValueAsString(errorEvent) + '\n');
                    } catch (Exception ex) {
                    }
                    emitter.completeWithError(e);
                } finally {
                    streaming.set(false);
                    heartbeatThread.interrupt();
                    // 清理 ThreadLocal 和会话级变量
                    SshExecuteAdkTool.clearCurrentTerminalSession();
                }

            }, "mvp-stream-" + sessionId).start();

            return emitter;
        } catch (Exception e) {
            log.error("流式对话失败", e);
            ResponseBodyEmitter emitter = new ResponseBodyEmitter();
            emitter.completeWithError(e);
            return emitter;
        }
    }
}
