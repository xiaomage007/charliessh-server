package com.charlie.ai.domain.ssh.model.valobj;

/**
 * 危险命令风险等级
 * <p>
 * 等级决定命令在 SSH 通道中的处置策略，判定入口见
 * {@code com.charlie.ai.domain.ssh.service.security.DangerousCommandDetector}。
 * <table border="1">
 *     <caption>等级与处置策略</caption>
 *     <tr><th>等级</th><th>处置策略</th></tr>
 *     <tr><td>SAFE</td><td>未命中任何风险规则，正常执行</td></tr>
 *     <tr><td>LOW</td><td>仅审计留痕，正常执行</td></tr>
 *     <tr><td>MEDIUM</td><td>正常执行，但回传风险提示并打印告警日志</td></tr>
 *     <tr><td>HIGH</td><td>默认拦截；仅运维白名单 allow-rules 可放行</td></tr>
 *     <tr><td>FORBIDDEN</td><td>永久拦截，不提供任何放行通道</td></tr>
 * </table>
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public enum DangerRiskLevel {

    /** 未命中任何风险规则 */
    SAFE(0, "安全"),

    /** 仅审计留痕，不影响执行 */
    LOW(1, "低风险"),

    /** 放行但告警，输出中附带风险提示 */
    MEDIUM(2, "中风险"),

    /** 默认拦截，需运维显式加入白名单方可放行 */
    HIGH(3, "高风险"),

    /** 永久拦截，无任何放行通道 */
    FORBIDDEN(4, "禁止执行");

    private final int weight;

    private final String desc;

    DangerRiskLevel(int weight, String desc) {
        this.weight = weight;
        this.desc = desc;
    }

    public int getWeight() {
        return weight;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 取两个等级中更严重的一个
     *
     * @param left  左值，可为 null
     * @param right 右值，可为 null
     * @return 更严重的等级
     */
    public static DangerRiskLevel max(DangerRiskLevel left, DangerRiskLevel right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.weight >= right.weight ? left : right;
    }

}
