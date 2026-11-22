package com.charlie.ai.domain.agent.service.intent.classifier.impl;

import com.charlie.ai.domain.agent.model.valobj.intent.ConversationContextVO;
import com.charlie.ai.domain.agent.model.valobj.intent.IntentResultVO;
import com.charlie.ai.domain.agent.model.valobj.intent.IntentRuleVO;
import com.charlie.ai.domain.agent.model.valobj.intent.IntentTypeEnumVO;
import com.charlie.ai.domain.agent.service.intent.classifier.IIntentClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则意图分类器（第1层，< 1ms）
 * <p>
 * 通过关键词匹配（最高 0.6）+ 正则匹配（+0.2）+ 上下文加权（+0.1）
 * 快速识别高置信度意图。置信度 ≥ 0.8 时直接返回，跳过 LLM 调用。
 *
 * <p>打分与裁决流程（{@link #classify} 实现）：
 * <pre>
 *   输入 message
 *     │
 *     ├─ 纯"继续"指令？ ──是──→ CONTINUE（有任务态 conf=0.95 / 无任务态 conf=0.4）
 *     │
 *     └─ 否 → 遍历每条 RULES 打分：
 *              关键词命中 0.5+(hits-1)*0.1 ≤0.7
 *              正则命中   +0.2
 *              上下文加权 +0.1（最近意图含同意图）
 *              ──────────────────────────
 *              按得分降序排列
 *     │
 *     ├─ ≥2 个意图 ≥0.5 → COMPOUND（候选=全部命中意图）
 *     ├─ 仅 1 个命中     → 单一意图（候选=次优意图）
 *     └─ 无任何命中      → UNKNOWN(conf=0)
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>"继续部署"不会被误判为 CONTINUE（去掉继续类词后仍剩"部署"，长度>4）。</li>
 *   <li>复合意图门槛 0.5，避免单一强信号被误报为 COMPOUND。</li>
 * </ul>
 *
 * @author Charlie
 * @since 2026/11/23
 */
@Slf4j
@Component
public class RuleIntentClassifier implements IIntentClassifier {

    @Override
    public IntentResultVO classify(String message, ConversationContextVO context) {
        return null;
    }

    /**
     * 这里提取了一些运维场景的常用操作，也可以按需补充意图关键词。
     */
    private static final List<IntentRuleVO> RULES = List.of(
            rule(IntentTypeEnumVO.DIAGNOSE,
                    List.of("挂了", "宕机", "down", "502", "503", "504", "OOM", "满", "过高", "异常",
                            "报错", "告警", "超时", "timeout", "crash", "panic", "fatal"),
                    List.of("为什么.*(?:挂|报错|失败|不通)", "排查.*问题", "分析.*原因",
                            "(?:宕机|挂了|报错|超时|OOM|crash|panic)")),

            rule(IntentTypeEnumVO.CONFIGURE,
                    List.of("配置", "config", "修改配置", "参数", "调整", "设置", "调优"),
                    List.of("修改.*(?:conf|cfg|yml|properties|xml|json)", "设置.*参数",
                            "(?:改|修改|调整|调优|设置).*(?:配置|参数|config)")),

            rule(IntentTypeEnumVO.DEPLOY,
                    List.of("部署", "deploy", "发布", "回滚", "rollback", "上线", "更新版本", "重启服务"),
                    List.of("(?:发布|部署).*版本", "回滚.*版本")),

            rule(IntentTypeEnumVO.MONITOR,
                    List.of("查看", "监控", "日志", "log", "cpu", "内存", "磁盘", "网络", "流量",
                            "负载", "load", "使用率", "状态", "top", "htop", "free", "df"),
                    List.of("(?:看|查|check).*(?:状态|情况|使用率)", "tail.*log", "(?:top|htop|free|df)(?: |$)")),

            rule(IntentTypeEnumVO.SECURITY,
                    List.of("防火墙", "firewall", "iptables", "权限", "permission", "ssh", "密钥",
                            "证书", "ssl", "tls", "安全", "漏洞", "CVE"),
                    List.of("(?:开放|关闭).*端口", "配置.*(?:ssl|证书|密钥)")),

            rule(IntentTypeEnumVO.BACKUP,
                    List.of("备份", "backup", "恢复", "restore", "导出", "import", "迁移"),
                    List.of("备份.*(?:数据库|文件|配置)", "恢复.*数据")),

            rule(IntentTypeEnumVO.EXPLAIN,
                    List.of("什么意思", "怎么理解", "解释", "说明", "explain", "what is", "how to"),
                    List.of("这个命令.*(?:意思|作用|用途)")),

            rule(IntentTypeEnumVO.SEARCH,
                    List.of("找", "搜索", "grep", "find", "locate", "查找", "哪个进程", "哪个文件",
                            "占用", "列出", "ls", "ps"),
                    List.of("(?:找|搜索).*(?:文件|进程|端口)", "(?:占用|哪个).*(?:端口|进程|文件)",
                            "(?:grep|find|locate|ps|ls)(?: |$)"))
    );

    private static IntentRuleVO rule(IntentTypeEnumVO intent, List<String> keywords,
            List<String> patterns) {
        /**
         * 工厂方法：构建 IntentRuleVO 对象。
         * <p>
         * 在静态初始化块中为每条规则创建 IntentRuleVO 实例，避免在 classify 时重复构建。
         *
         * @param intent    意图类型
         * @param keywords  关键词列表（用于快速匹配）
         * @param patterns  正则表达式列表（用于精确模式匹配）
         * @return 构建好的 IntentRuleVO 实例
         */
        IntentRuleVO r = new IntentRuleVO();
        r.setIntent(intent);
        r.setKeywords(keywords);
        r.setPatterns(patterns);
        return r;
    }

}