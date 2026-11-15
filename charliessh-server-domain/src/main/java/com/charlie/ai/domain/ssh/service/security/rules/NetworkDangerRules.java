package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.LOW;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 网络与防护类规则
 * <p>
 * 覆盖：iptables 规则清空、firewalld / ufw 关闭、网络接口关闭、路由删除。
 * 这类操作会直接改变主机的网络可达性，误操作后往往连 SSH 都连不上。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class NetworkDangerRules {

    private NetworkDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                iptablesFlush(),
                iptablesPolicy(),
                ufwDisable(),
                firewalldRemoveAll(),
                interfaceDown(),
                linkDown(),
                addressFlush(),
                routeDelete(),
                arpTable()
        );
    }

    /**
     * 清空 iptables 规则
     */
    private static CommandRule iptablesFlush() {
        return new CommandRule("NET_IPTABLES_FLUSH", HIGH, "网络与防护",
                cmd -> "清空 iptables 规则会让主机失去网络防护: " + cmd.getRaw(),
                cmd -> cmd.is("iptables", "ip6tables")
                        && (cmd.hasArg("-F") || cmd.hasArg("-X") || cmd.hasArg("--flush")));
    }

    /**
     * 修改 iptables 默认策略
     */
    private static CommandRule iptablesPolicy() {
        return new CommandRule("NET_IPTABLES_POLICY", HIGH, "网络与防护",
                cmd -> "修改 iptables 默认策略会改变主机网络可达性: " + cmd.getRaw(),
                cmd -> cmd.is("iptables", "ip6tables")
                        && cmd.hasArg("-P")
                        && (cmd.hasArg("ACCEPT") || cmd.hasArg("DROP")));
    }

    /**
     * 关闭或重置 ufw
     */
    private static CommandRule ufwDisable() {
        return new CommandRule("NET_UFW_DISABLE", HIGH, "网络与防护",
                cmd -> "关闭或重置防火墙会暴露主机: " + cmd.getRaw(),
                cmd -> cmd.is("ufw")
                        && ("disable".equals(cmd.getFirstOperand()) || "reset".equals(cmd.getFirstOperand())));
    }

    /**
     * 移除全部防火墙规则
     */
    private static CommandRule firewalldRemoveAll() {
        return new CommandRule("NET_FIREWALL_REMOVE", HIGH, "网络与防护",
                cmd -> "移除全部防火墙规则会暴露主机: " + cmd.getRaw(),
                cmd -> cmd.is("firewall-cmd")
                        && (cmd.hasArg("--remove-all") || cmd.hasArg("--panic-off")));
    }

    /**
     * ifconfig <iface> down：会立即断开 SSH 连接
     */
    private static CommandRule interfaceDown() {
        return new CommandRule("NET_IFACE_DOWN", HIGH, "网络与防护",
                cmd -> "关闭网络接口会立即断开 SSH 连接: " + cmd.getRaw(),
                cmd -> cmd.is("ifconfig") && cmd.hasArg("down") && !cmd.hasArg("lo"));
    }

    /**
     * ip link set <iface> down
     */
    private static CommandRule linkDown() {
        return new CommandRule("NET_LINK_DOWN", HIGH, "网络与防护",
                cmd -> "关闭网络链路会立即断开 SSH 连接: " + cmd.getRaw(),
                cmd -> cmd.is("ip") && cmd.hasArg("link") && cmd.hasArg("down"));
    }

    /**
     * 清空网络地址配置
     */
    private static CommandRule addressFlush() {
        return new CommandRule("NET_ADDR_FLUSH", MEDIUM, "网络与防护",
                cmd -> "清空网络地址配置: " + cmd.getRaw(),
                cmd -> cmd.is("ip") && cmd.hasArg("flush"));
    }

    /**
     * 删除路由
     */
    private static CommandRule routeDelete() {
        return new CommandRule("NET_ROUTE_DEL", HIGH, "网络与防护",
                cmd -> "删除路由会导致主机失联: " + cmd.getRaw(),
                cmd -> cmd.is("route")
                        && ("del".equals(cmd.getFirstOperand()) || "flush".equals(cmd.getFirstOperand())));
    }

    /**
     * 修改 ARP 表
     */
    private static CommandRule arpTable() {
        return new CommandRule("NET_ARP", LOW, "网络与防护",
                cmd -> "修改 ARP 表: " + cmd.getRaw(),
                cmd -> cmd.is("arp"));
    }

}
