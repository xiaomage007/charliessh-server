package com.charlie.ai.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 里程碑事件
 * @Author: Charlie
 * @Date: 2026/11/15 12:20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneVO {

    /**
     * 事件类型
     */
    private Type type;
    /**
     * 事件内容（截断保存）
     */
    private String content;
    /**
     * 发生时间
     */
    private long timestamp;

    public enum Type {
        TASK_CHANGE,        // 任务切换：用户说“换个思路”
        TASK_COMPLETE,      // 任务完成：用户说“搞定了”
        USER_CORRECTION,    // 用户纠偏：用户说“不对/不要”
        ERROR,              // 工具执行报错
        DECISION,           // 重要决策（预留）
        FILE_SWITCH         // 文件切换（预留）
    }

}