package com.charlie.ai.api.dto;

import lombok.Data;

/**
 * @description:
 * @author: Charlie
 * @date: 2026/10/20 18:57
 */
@Data
public class ChatRequestDTO {

    private String agentId;
    private String userId;
    private String sessionId;
    private String message;

}
