package com.offlinepw.vault.crypto;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {
    private static final String KEY_ALIAS = "OfflinePW_Master_Key_v3";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    public CryptoManager(Context context) {
        initKey();
    }

    private synchronized void initKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return;
            }

            boolean generated = false;
            // ۱. تلاش اول: ایجاد کلید در ماژول سخت‌افزاری اختصاصی StrongBox
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                    KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(false)
                            .setIsStrongBoxBacked(true)
                            .build();
                    kg.init(spec);
                    kg.generateKey();
                    generated = true;
                } catch (Throwable ignored) {
                    generated = false;
                }
            }

            // ۲. فال‌بک به محیط امن سخت‌افزاری استاندارد (TEE)
            if (!generated) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setUserAuthenticationRequired(false)
                        .build();
                kg.init(spec);
                kg.generateKey();
            }
        } catch (Throwable ignored) {
        }
    }

    private SecretKey getSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            Key key = keyStore.getKey(KEY_ALIAS, null);
            if (key instanceof SecretKey) {
                return (SecretKey) key;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return "";
        try {
            SecretKey key = getSecretKey();
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Throwable e) {
            return "";
        }
    }

    public String decrypt(String base64) {
        if (base64 == null || base64.isEmpty()) return "";
        try {
            SecretKey key = getSecretKey();
            if (key == null) return "";
            byte[] combined = Base64.decode(base64, Base64.NO_WRAP);
            if (combined == null || combined.length < 12) return "";
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, 12);
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }
}
