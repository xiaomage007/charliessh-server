package com.charlie.ai.api;

import com.charlie.ai.api.dto.*;
import com.charlie.ai.api.response.Response;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * @description: 智能体服务接口
 * @author: Charlie
 * @date: 2026/10/20 18:50
 */
public interface IAgentService {

    Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList();

    Response<CreateSessionResponseDTO> createSession(CreateSessionRequestDTO requestDTO);

    Response<ChatResponseDTO> chat(ChatRequestDTO requestDTO);

    ResponseBodyEmitter chatStream(ChatRequestDTO requestDTO);

}
