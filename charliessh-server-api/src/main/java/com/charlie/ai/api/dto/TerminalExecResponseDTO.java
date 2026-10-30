package com.charlie.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行命令响应
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TerminalExecResponseDTO {

    /** 命令输出 */
    private String output;

}
