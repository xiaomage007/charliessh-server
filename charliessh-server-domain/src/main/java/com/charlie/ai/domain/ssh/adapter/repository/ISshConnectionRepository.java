package com.charlie.ai.domain.ssh.adapter.repository;


import com.charlie.ai.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionEntity;

import java.util.List;

/**
 * SSH连接仓储接口（领域层定义，基础设施层实现）
 *
 */
public interface ISshConnectionRepository {

    /**
     * 保存SSH连接配置
     */
    void saveConnection(SshConnectionEntity entity);

    /**
     * 更新SSH连接配置
     */
    void updateConnection(SshConnectionEntity entity);

    /**
     * 删除SSH连接配置
     */
    void deleteConnection(String connectionId);

    /**
     * 根据连接ID查询（凭证保持数据库原样，不解密）
     */
    SshConnectionEntity queryConnectionById(String connectionId);

    /**
     * 根据连接ID查询并解密凭证（仅用于建立 SSH 会话等确实需要明文凭证的场景）
     * <p>凭证解密失败时抛出异常，异常信息中包含连接标识与处理建议。</p>
     */
    SshConnectionEntity queryConnectionWithCredential(String connectionId);

    /**
     * 查询用户的所有连接
     */
    List<SshConnectionEntity> queryConnectionListByUserId(String userId);

    /**
     * 保存/更新高级配置
     */
    void saveConnectionConfig(SshConnectionConfigEntity entity);

    /**
     * 根据连接ID查询高级配置
     */
    SshConnectionConfigEntity queryConnectionConfigById(String connectionId);

}
