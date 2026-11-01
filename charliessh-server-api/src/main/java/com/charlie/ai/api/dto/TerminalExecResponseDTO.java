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

    /** 命令退出码，0 表示成功，-1 表示服务端未回传 */
    private Integer exitCode;

    /** 是否执行成功 */
    private Boolean success;

}
