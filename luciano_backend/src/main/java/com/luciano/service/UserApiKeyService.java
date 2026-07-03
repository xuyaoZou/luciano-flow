package com.luciano.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luciano.entity.ModelProvider;
import com.luciano.entity.UserApiKey;
import com.luciano.repository.mapper.ModelProviderMapper;
import com.luciano.repository.mapper.UserApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 用户 API Key 管理服务
 * 双 Key 模式核心：用户自带 Key 的 CRUD 和验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiKeyService {

    private final UserApiKeyMapper userApiKeyMapper;
    private final ModelProviderMapper modelProviderMapper;

    @Value("${luciano.encryption.aes-key:luciano-platform-encryption-key-must-be-32-bytes}")
    private String aesKeyStr;

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private final SecureRandom random = new SecureRandom();

    /**
     * 获取用户所有 API Key（脱敏）
     */
    public List<UserApiKey> listByUserId(Long userId) {
        List<UserApiKey> keys = userApiKeyMapper.selectList(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getIsActive, true)
        );
        // 脱敏：只显示 key 的掩码
        keys.forEach(k -> k.setEncryptedKey(maskApiKey(decrypt(k.getEncryptedKey()))));
        return keys;
    }

    /**
     * 添加用户 API Key
     */
    @Transactional
    public UserApiKey addKey(Long userId, String providerName, String apiKey, String baseUrl) {
        // 验证 provider 是否存在
        ModelProvider provider = modelProviderMapper.selectOne(
                new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getName, providerName)
        );
        if (provider == null) {
            throw new IllegalArgumentException("未知的模型服务商: " + providerName);
        }

        // 检查是否已存在
        UserApiKey existing = userApiKeyMapper.selectOne(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getProviderName, providerName)
        );
        if (existing != null) {
            throw new IllegalArgumentException("已存在该服务商的 API Key，请使用更新接口");
        }

        // 验证 Key 有效性（TODO: 调用各厂商验证接口）
        // 先简单存储，后续接入实际验证

        UserApiKey key = new UserApiKey();
        key.setUserId(userId);
        key.setProviderName(providerName);
        key.setEncryptedKey(encrypt(apiKey));
        key.setBaseUrl(baseUrl);
        key.setIsActive(true);
        key.setLastVerified(OffsetDateTime.now());
        key.setCreatedAt(OffsetDateTime.now());
        key.setUpdatedAt(OffsetDateTime.now());
        userApiKeyMapper.insert(key);

        // 返回脱敏版本
        key.setEncryptedKey(maskApiKey(apiKey));
        return key;
    }

    /**
     * 更新用户 API Key
     */
    @Transactional
    public UserApiKey updateKey(Long userId, Long keyId, String apiKey, String baseUrl) {
        UserApiKey key = userApiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new IllegalArgumentException("API Key 不存在");
        }

        if (apiKey != null) {
            key.setEncryptedKey(encrypt(apiKey));
            key.setLastVerified(OffsetDateTime.now());
        }
        if (baseUrl != null) {
            key.setBaseUrl(baseUrl);
        }
        key.setUpdatedAt(OffsetDateTime.now());
        userApiKeyMapper.updateById(key);

        key.setEncryptedKey(maskApiKey(apiKey != null ? apiKey : "***"));
        return key;
    }

    /**
     * 删除用户 API Key（软删除：设为 inactive）
     */
    @Transactional
    public void deleteKey(Long userId, Long keyId) {
        UserApiKey key = userApiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new IllegalArgumentException("API Key 不存在");
        }
        key.setIsActive(false);
        key.setUpdatedAt(OffsetDateTime.now());
        userApiKeyMapper.updateById(key);
    }

    /**
     * 获取用户指定 provider 的解密 API Key
     * 用于 ModelGateway 路由时获取真实 Key
     */
    public String getDecryptedKey(Long userId, String providerName) {
        UserApiKey key = userApiKeyMapper.selectOne(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getProviderName, providerName)
                        .eq(UserApiKey::getIsActive, true)
        );
        if (key == null) {
            return null;
        }
        return decrypt(key.getEncryptedKey());
    }

    /**
     * 获取用户指定 provider 的 base URL
     */
    public String getUserBaseUrl(Long userId, String providerName) {
        UserApiKey key = userApiKeyMapper.selectOne(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getProviderName, providerName)
                        .eq(UserApiKey::getIsActive, true)
        );
        if (key == null || key.getBaseUrl() == null) {
            return null;
        }
        return key.getBaseUrl();
    }

    /**
     * 获取用户指定 provider 的脱敏 API Key（用于展示）
     */
    public String getMaskedKey(Long userId, String providerName) {
        UserApiKey key = userApiKeyMapper.selectOne(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getProviderName, providerName)
                        .eq(UserApiKey::getIsActive, true)
        );
        if (key == null) {
            return null;
        }
        return maskApiKey(decrypt(key.getEncryptedKey()));
    }

    // ==================== AES 加解密 ====================

    private SecretKeySpec getAesKey() {
        byte[] keyBytes = aesKeyStr.getBytes();
        byte[] keyBytes32 = new byte[32];
        System.arraycopy(keyBytes, 0, keyBytes32, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(keyBytes32, AES_ALGORITHM);
    }

    /**
     * AES-256-GCM 加密
     */
    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getAesKey(), gcmSpec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            // 格式: iv(12字节) + ciphertext + tag
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密
     */
    private String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getAesKey(), gcmSpec);

            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 掩码 API Key：只显示前4位和后4位
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }
}