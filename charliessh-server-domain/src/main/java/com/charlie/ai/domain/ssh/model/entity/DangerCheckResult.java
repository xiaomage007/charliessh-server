package com.charlie.ai.domain.ssh.model.entity;

import com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 危险命令检测结果
 * <p>
 * 由 {@code DangerousCommandDetector} 产出，既用于服务端审计留痕，
 * 也用于把结构化的拦截原因回传给大模型（避免模型无意义重试或尝试绕过）。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DangerCheckResult {

    /** 是否被拦截（true 表示命令不应下发到目标服务器） */
    private boolean blocked;

    /** 命中的最高风险等级 */
    private DangerRiskLevel riskLevel;

    /** 命中的规则编号，便于运维按规则粒度审计与放行 */
    private String ruleId;

    /** 风险分类，如「文件破坏」「系统致命」 */
    private String category;

    /** 风险说明，描述命中原因 */
    private String reason;

    /** 处置建议，回传给模型 / 用户 */
    private String suggestion;

    /** 被检测的原始命令 */
    private String command;

    /** 本次命中的全部规则描述 */
    @Builder.Default
    private List<String> hits = new ArrayList<>();

}
