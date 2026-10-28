package com.charlie.ai.infrastructure.dao;

import com.charlie.ai.infrastructure.dao.po.SshConnectionConfigPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSH连接高级配置DAO
 *
 */
@Mapper
public interface ISshConnectionConfigDAO {

    /**
     * 插入或更新SSH连接高级配置
     *
     * @param po 配置对象
     */
    void insertOrUpdate(SshConnectionConfigPO po);

    /**
     * 根据连接ID查询高级配置
     *
     * @param connectionId 连接唯一标识
     * @return 配置对象
     */
    SshConnectionConfigPO queryByConnectionId(String connectionId);

}
