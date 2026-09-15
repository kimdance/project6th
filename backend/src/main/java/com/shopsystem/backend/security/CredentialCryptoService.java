package com.shopsystem.backend.security;

import com.shopsystem.backend.config.CryptoProperties;

import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 決済手段の接続情報（FR-B05）の暗号化・復号。平文は {@code payment_method_config.credential_enc}
 * に保存せず、常にこのサービスを通した暗号文（バイト列）を保存する。
 */
@Component
public class CredentialCryptoService {

    private final BytesEncryptor encryptor;

    public CredentialCryptoService(CryptoProperties properties) {
        this.encryptor = Encryptors.stronger(properties.getSecret(), properties.getSalt());
    }

    public byte[] encrypt(String plaintext) {
        return encryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public String decrypt(byte[] cipherBytes) {
        return new String(encryptor.decrypt(cipherBytes), StandardCharsets.UTF_8);
    }
}
