package com.charlie.ai.infrastructure.adapter.repository;

import com.charlie.ai.domain.ssh.adapter.repository.ISshConnectionRepository;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.charlie.ai.domain.ssh.model.entity.SshConnectionEntity;
import com.charlie.ai.domain.ssh.model.valobj.AuthTypeEnum;
import com.charlie.ai.domain.ssh.model.valobj.ConnectionStatusEnum;
import com.charlie.ai.infrastructure.dao.ISshConnectionConfigDAO;
import com.charlie.ai.infrastructure.dao.ISshConnectionDAO;
import com.charlie.ai.infrastructure.dao.po.SshConnectionConfigPO;
import com.charlie.ai.infrastructure.dao.po.SshConnectionPO;
import com.charlie.ai.infrastructure.security.PasswordEncryptor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SSH连接仓储实现
 *
 */
@Slf4j
@Repository
public class SshConnectionRepository implements ISshConnectionRepository {

    @Resource
    private ISshConnectionDAO sshConnectionDAO;
    @Resource
    private ISshConnectionConfigDAO sshConnectionConfigDAO;

    private final PasswordEncryptor passwordEncryptor = new PasswordEncryptor();

    @Override
    public void saveConnection(SshConnectionEntity entity) {
        sshConnectionDAO.insert(toPO(entity));
    }

    @Override
    public void updateConnection(SshConnectionEntity entity) {
        sshConnectionDAO.update(toPO(entity));
    }

    @Override
    public void deleteConnection(String connectionId) {
        sshConnectionDAO.delete(connectionId);
    }

    @Override
    public SshConnectionEntity queryConnectionById(String connectionId) {
        SshConnectionPO po = sshConnectionDAO.queryByConnectionId(connectionId);
        return po != null ? toEntity(po, false) : null;
    }

    @Override
    public SshConnectionEntity queryConnectionWithCredential(String connectionId) {
        SshConnectionPO po = sshConnectionDAO.queryByConnectionId(connectionId);
        return po != null ? toEntity(po, true) : null;
    }

    @Override
    public List<SshConnectionEntity> queryConnectionListByUserId(String userId) {
        return sshConnectionDAO.queryListByUserId(userId).stream()
                .map(po -> toEntity(po, false))
                .collect(Collectors.toList());
    }

    @Override
    public void saveConnectionConfig(SshConnectionConfigEntity entity) {
        sshConnectionConfigDAO.insertOrUpdate(toConfigPO(entity));
    }

    @Override
    public SshConnectionConfigEntity queryConnectionConfigById(String connectionId) {
        SshConnectionConfigPO po = sshConnectionConfigDAO.queryByConnectionId(connectionId);
        return po != null ? toConfigEntity(po) : null;
    }

    // ========== Entity <-> PO 转换 ==========

    private SshConnectionPO toPO(SshConnectionEntity entity) {
        // 加密密码和私钥
        String encryptedPassword = entity.getPassword();
        String encryptedPrivateKey = entity.getPrivateKey();
        Integer encryptedFlag = entity.getEncrypted();

        if (encryptedPassword != null && !encryptedPassword.isEmpty()) {
            if (!passwordEncryptor.isEncrypted(encryptedPassword)) {
                encryptedPassword = passwordEncryptor.encrypt(encryptedPassword);
                encryptedFlag = 1;
            } else if (!passwordEncryptor.canDecrypt(encryptedPassword)) {
                log.warn("连接[{}]的密码密文无法用当前密钥解密(密钥可能已变更)，将保留原密文，请重新录入密码", entity.getConnectionId());
            }
        }
        if (encryptedPrivateKey != null && !encryptedPrivateKey.isEmpty()) {
            if (!passwordEncryptor.isEncrypted(encryptedPrivateKey)) {
                encryptedPrivateKey = passwordEncryptor.encrypt(encryptedPrivateKey);
                encryptedFlag = 1;
            } else if (!passwordEncryptor.canDecrypt(encryptedPrivateKey)) {
                log.warn("连接[{}]的私钥密文无法用当前密钥解密(密钥可能已变更)，将保留原密文，请重新录入私钥", entity.getConnectionId());
            }
        }

        return SshConnectionPO.builder()
                .id(entity.getId())
                .connectionId(entity.getConnectionId())
                .connectionName(entity.getConnectionName())
                .host(entity.getHost())
                .port(entity.getPort())
                .username(entity.getUsername())
                .authType(entity.getAuthType() != null ? entity.getAuthType().getCode() : AuthTypeEnum.PASSWORD.getCode())
                .password(encryptedPassword)
                .privateKey(encryptedPrivateKey)
                .encrypted(encryptedFlag)
                .status(entity.getStatus() != null ? entity.getStatus().getCode() : ConnectionStatusEnum.DISCONNECTED.getCode())
                .userId(entity.getUserId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * PO -> Entity
     *
     * @param decryptCredential 是否解密凭证（密码/私钥）。
     *                           列表、详情、更新等场景不需要明文凭证，不解密可避免
     *                           单条脏数据导致整个列表查询失败；只有建立 SSH 会话时才解密。
     */
    private SshConnectionEntity toEntity(SshConnectionPO po, boolean decryptCredential) {
        // 解密密码和私钥
        String password = po.getPassword();
        String privateKey = po.getPrivateKey();

        if (decryptCredential && po.getEncrypted() != null && po.getEncrypted() == 1) {
            password = decryptCredential(password, po.getConnectionId(), "密码");
            privateKey = decryptCredential(privateKey, po.getConnectionId(), "私钥");
        }

        return SshConnectionEntity.builder()
                .id(po.getId())
                .connectionId(po.getConnectionId())
                .connectionName(po.getConnectionName())
                .host(po.getHost())
                .port(po.getPort())
                .username(po.getUsername())
                .authType(AuthTypeEnum.fromCode(po.getAuthType()))
                .password(password)
                .privateKey(privateKey)
                .encrypted(po.getEncrypted())
                .status(ConnectionStatusEnum.fromCode(po.getStatus()))
                .userId(po.getUserId())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    /**
     * 解密凭证，失败时抛出带连接标识的可读异常
     */
    private String decryptCredential(String value, String connectionId, String label) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            return passwordEncryptor.decrypt(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("连接[" + connectionId + "]" + label + "解密失败: " + e.getMessage(), e);
        }
    }

    private SshConnectionConfigPO toConfigPO(SshConnectionConfigEntity entity) {
        return SshConnectionConfigPO.builder()
                .id(entity.getId())
                .connectionId(entity.getConnectionId())
                .connectTimeout(entity.getConnectTimeout())
                .keepaliveInterval(entity.getKeepaliveInterval())
                .startupCommand(entity.getStartupCommand())
                .compression(booleanToInt(entity.getCompression()))
                .strictHostKeyCheck(booleanToInt(entity.getStrictHostKeyCheck()))
                .knownHosts(entity.getKnownHosts())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private SshConnectionConfigEntity toConfigEntity(SshConnectionConfigPO po) {
        return SshConnectionConfigEntity.builder()
                .id(po.getId())
                .connectionId(po.getConnectionId())
                .connectTimeout(po.getConnectTimeout())
                .keepaliveInterval(po.getKeepaliveInterval())
                .startupCommand(po.getStartupCommand())
                .compression(intToBoolean(po.getCompression()))
                .strictHostKeyCheck(intToBoolean(po.getStrictHostKeyCheck()))
                .knownHosts(po.getKnownHosts())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private Integer booleanToInt(Boolean val) {
        return Boolean.TRUE.equals(val) ? 1 : 0;
    }

    private Boolean intToBoolean(Integer val) {
        return val != null && val == 1;
    }

}
