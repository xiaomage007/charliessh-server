package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.policy.SensitivePathPolicy;
import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.FORBIDDEN;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 权限与账号类规则
 * <p>
 * 覆盖：账号增删、口令修改、sudoers 提权、ACL / 能力位变更、账号配置文件覆盖。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class PrivilegeDangerRules {

    private PrivilegeDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                deleteAccount(),
                changePassword(),
                editSudoers(),
                accessControl(),
                redirectAccountConfig()
        );
    }

    /**
     * 删除用户 / 用户组：可能导致账号体系损坏甚至失去登录能力
     */
    private static CommandRule deleteAccount() {
        return new CommandRule("PRIV_USER_DELETE", HIGH, "权限与账号",
                cmd -> "删除用户/用户组可能导致账号体系损坏甚至失去登录能力: " + cmd.getRaw(),
                cmd -> cmd.is("userdel", "groupdel"));
    }

    /**
     * 修改账号口令
     */
    private static CommandRule changePassword() {
        return new CommandRule("PRIV_PASSWD", MEDIUM, "权限与账号",
                cmd -> "修改账号口令可能造成他人无法登录: " + cmd.getRaw(),
                cmd -> cmd.is("passwd", "chpasswd"));
    }

    /**
     * 编辑 sudoers 属于提权操作
     */
    private static CommandRule editSudoers() {
        return new CommandRule("PRIV_VISUDO", HIGH, "权限与账号",
                cmd -> "修改 sudoers 属于提权操作: " + cmd.getRaw(),
                cmd -> cmd.is("visudo"));
    }

    /**
     * 修改文件访问控制列表 / 能力位
     */
    private static CommandRule accessControl() {
        return new CommandRule("PRIV_ACL", MEDIUM, "权限与账号",
                cmd -> "修改文件访问控制列表/能力位: " + cmd.getRaw(),
                cmd -> cmd.is("setfacl", "setcap"));
    }

    /**
     * 输出将覆盖账号 / 提权配置文件
     */
    private static CommandRule redirectAccountConfig() {
        return new CommandRule("PRIV_REDIRECT_ACCOUNT", FORBIDDEN, "权限与账号",
                cmd -> "命令将覆盖账号/提权配置文件: " + cmd.getRaw(),
                cmd -> cmd.getRedirects().stream().anyMatch(PrivilegeDangerRules::isAccountConfig));
    }

    private static boolean isAccountConfig(String target) {
        String path = SensitivePathPolicy.normalizePath(target);
        return path.startsWith("/etc/sudoers")
                || path.startsWith("/etc/passwd")
                || path.startsWith("/etc/shadow");
    }

}
