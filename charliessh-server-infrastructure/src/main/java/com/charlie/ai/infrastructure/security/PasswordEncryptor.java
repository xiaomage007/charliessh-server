package com.charlie.ai.infrastructure.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加解密工具
 * 使用 AES-256-GCM 对称加密，密钥从环境变量或默认值读取
 *
 * <p>密文格式：{@code ENC:{Base64(IV + 密文 + GCM Tag)}}。
 * 前缀用于把密文与“恰好长得像 Base64 的明文”区分开，
 * 避免明文被误判成密文（或密文被误判成明文）后二次加密。
 * 解密侧对不带前缀的旧格式保持兼容。</p>
 */
@Slf4j
public class PasswordEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    /** GCM 认证标签字节数 */
    private static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH / 8;
    /** 合法密文的最短长度：IV + GCM Tag */
    private static final int MIN_CIPHERTEXT_LENGTH = GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES;
    /** 密文格式标记前缀 */
    public static final String CIPHER_PREFIX = "ENC:";

    /**
     * 默认密钥（生产环境务必通过环境变量 CharlisSSH_SECRET_KEY 覆盖）
     */
    private static final String DEFAULT_SECRET_KEY = "CharlisSSH2026SecretKey!!";

    private final SecretKeySpec keySpec;

    public PasswordEncryptor() {
        this(null);
    }

    public PasswordEncryptor(String secretKey) {
        String key = secretKey;
        if (key == null || key.isBlank()) {
            key = System.getenv("CharlisSSH_SECRET_KEY");
        }
        if (key == null || key.isBlank()) {
            key = DEFAULT_SECRET_KEY;
            log.warn("未配置 CharlisSSH_SECRET_KEY 环境变量，使用默认密钥，生产环境请务必配置！");
        }
        // 确保密钥长度为 32 字节（AES-256）
        byte[] keyBytes = padOrTrim(key.getBytes(StandardCharsets.UTF_8), 32);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密明文，返回带格式前缀的 Base64 密文（ENC:{IV + 密文}）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV + 密文拼接后 Base64
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("密码加密失败", e);
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    /**
     * 解密密文，返回明文
     *
     * @throws IllegalArgumentException 密文格式非法、或无法用当前密钥解密（密钥变更、脏数据等）
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(stripPrefix(ciphertext));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("密文不是合法的 Base64 编码，请重新录入密码", e);
        }
        if (combined.length < MIN_CIPHERTEXT_LENGTH) {
            throw new IllegalArgumentException("密文长度非法(期望至少 " + MIN_CIPHERTEXT_LENGTH + " 字节，实际 "
                    + combined.length + ")，疑似以明文存储，请重新录入密码");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("密码解密失败", e);
            throw new IllegalArgumentException("密码解密失败，密钥可能已变更，请重新录入密码", e);
        }
    }

    /**
     * 判断字符串是否是加密后的格式（按格式判断，不校验能否解密）
     * <p>带 ENC: 前缀的一定是密文；不带前缀的按旧格式兼容：Base64 且解码后长度符合密文结构。</p>
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith(CIPHER_PREFIX)) {
            return true;
        }
        try {
            // 兼容旧格式（无前缀）：Base64 且解码后长度符合密文结构
            return Base64.getDecoder().decode(value).length >= MIN_CIPHERTEXT_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 判断字符串能否用当前密钥成功解密
     */
    public boolean canDecrypt(String value) {
        try {
            decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 去掉密文格式前缀（兼容不带前缀的旧格式）
     */
    private String stripPrefix(String value) {
        return value.startsWith(CIPHER_PREFIX) ? value.substring(CIPHER_PREFIX.length()) : value;
    }

    private byte[] padOrTrim(byte[] bytes, int length) {
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, 0, Math.min(bytes.length, length));
        return result;
    }
}
