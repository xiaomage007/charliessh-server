package com.charlie.ai.domain.ssh.service.security.rules;

import com.charlie.ai.domain.ssh.service.security.rule.CommandRule;
import com.charlie.ai.domain.ssh.service.security.rule.DangerRule;

import java.util.List;

import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.HIGH;
import static com.charlie.ai.domain.ssh.model.valobj.DangerRiskLevel.MEDIUM;

/**
 * 容器与编排类规则
 * <p>
 * 覆盖：Docker / Podman 的清理与删除、Compose 的 down -v、Kubernetes 资源删除。
 * 这类操作的破坏性常被低估——{@code docker system prune -a} 会连带清理仍被引用的镜像，
 * {@code docker-compose down -v} 会连同数据卷一起删除。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public final class ContainerDangerRules {

    private ContainerDangerRules() {
    }

    public static List<DangerRule> rules() {
        return List.of(
                pruneAll(),
                removeContainerOrImage(),
                removeVolume(),
                composeDownWithVolume(),
                kubectlDeleteAll(),
                kubectlDelete()
        );
    }

    /**
     * docker system prune：清理镜像 / 容器 / 卷
     */
    private static CommandRule pruneAll() {
        return new CommandRule("CTR_PRUNE_ALL", HIGH, "容器与编排",
                cmd -> "docker system prune 会清理镜像/容器/卷，可能删除仍在使用的资源: " + cmd.getRaw(),
                cmd -> cmd.is("docker", "podman")
                        && "system".equals(cmd.getFirstOperand())
                        && cmd.hasArg("prune"));
    }

    /**
     * 删除容器或镜像
     */
    private static CommandRule removeContainerOrImage() {
        return new CommandRule("CTR_REMOVE", MEDIUM, "容器与编排",
                cmd -> "删除容器或镜像会导致服务中断: " + cmd.getRaw(),
                cmd -> cmd.is("docker", "podman")
                        && ("rm".equals(cmd.getFirstOperand()) || "rmi".equals(cmd.getFirstOperand())));
    }

    /**
     * 删除数据卷：会造成数据永久丢失
     */
    private static CommandRule removeVolume() {
        return new CommandRule("CTR_VOLUME_RM", HIGH, "容器与编排",
                cmd -> "删除数据卷会造成数据永久丢失: " + cmd.getRaw(),
                cmd -> cmd.is("docker", "podman")
                        && "volume".equals(cmd.getFirstOperand())
                        && cmd.hasArg("rm"));
    }

    /**
     * docker-compose down -v：连同数据卷一起删除
     */
    private static CommandRule composeDownWithVolume() {
        return new CommandRule("CTR_COMPOSE_DOWN_V", HIGH, "容器与编排",
                cmd -> "docker-compose down -v 会连同数据卷一起删除: " + cmd.getRaw(),
                cmd -> cmd.is("docker-compose", "docker-compose.yml", "podman-compose")
                        && cmd.hasArg("down")
                        && cmd.hasArg("-v"));
    }

    /**
     * 删除命名空间 / 全部资源：集群级影响
     */
    private static CommandRule kubectlDeleteAll() {
        return new CommandRule("CTR_K8S_DELETE_ALL", HIGH, "容器与编排",
                cmd -> "删除命名空间/全部资源会造成集群级影响: " + cmd.getRaw(),
                cmd -> cmd.is("kubectl")
                        && "delete".equals(cmd.getFirstOperand())
                        && (cmd.hasArg("namespace") || cmd.hasArg("--all") || cmd.hasArg("ns")));
    }

    /**
     * 删除 Kubernetes 资源
     */
    private static CommandRule kubectlDelete() {
        return new CommandRule("CTR_K8S_DELETE", MEDIUM, "容器与编排",
                cmd -> "删除 Kubernetes 资源会导致服务中断: " + cmd.getRaw(),
                cmd -> cmd.is("kubectl") && "delete".equals(cmd.getFirstOperand()));
    }

}
