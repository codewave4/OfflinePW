package com.offlinepw.vault;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * بکاپِ رمزنگاری‌شده‌ی ولت.
 *
 * یک «رمز بکاپ»ِ جدا از رمز مستر از کاربر گرفته می‌شود و از آن با PBKDF2-SHA256
 * (۶۰۰هزار دور + نمک تصادفی ۱۶ بایتی) کلید جداگانه‌ای مشتق می‌شود که کل JSON ولت را
 * با AES-256-GCM رمزنگاری می‌کند. بدین ترتیب فایل بکاپ بدون «رمز بکاپ» کاملاً
 * غیرقابل‌خواندن است (zero-knowledge حفظ می‌شود) و حتی اگر رمز مستر عوض شود،
 * بکاپ‌های قدیمی باز هم باز می‌شوند.
 *
 * قالب فایل (JSON):
 *   { "format": "offlinepw-backup-v1", "salt": <b64>, "iv": <b64>, "data": <b64> }
 */
public final class BackupManager {
    public static final String FORMAT = "offlinepw-backup-v1";

    private static final int PBKDF2_ITERATIONS = 600000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int GCM_TAG_BITS = 128;

    private BackupManager() {
    }

    /**
     * JSON ولت را با رمز بکاپ رمزنگاری کرده و بایت‌های فایل بکاپ را برمی‌گرداند.
     */
    public static byte[] encrypt(String vaultJson, String backupPassword) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        SecretKey key = deriveKey(backupPassword, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV(); // GCM: IV تصادفی ۱۲ بایتی
        byte[] data = cipher.doFinal(vaultJson.getBytes(StandardCharsets.UTF_8));

        JSONObject wrapper = new JSONObject();
        wrapper.put("format", FORMAT);
        wrapper.put("salt", Base64.getEncoder().encodeToString(salt));
        wrapper.put("iv", Base64.getEncoder().encodeToString(iv));
        wrapper.put("data", Base64.getEncoder().encodeToString(data));
        return wrapper.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * فایل بکاپ را با رمز بکاپ رمزگشایی کرده و JSON ولت را برمی‌گرداند.
     * در صورت رمز اشتباه، استثنای رمزنگاری (AEAD bad tag) رخ می‌دهد.
     */
    public static String decrypt(byte[] fileBytes, String backupPassword) throws Exception {
        JSONObject wrapper = new JSONObject(new String(fileBytes, StandardCharsets.UTF_8));
        if (!FORMAT.equals(wrapper.optString("format", ""))) {
            throw new IllegalArgumentException("not-an-offlinepw-backup");
        }
        byte[] salt = Base64.getDecoder().decode(wrapper.getString("salt"));
        byte[] iv = Base64.getDecoder().decode(wrapper.getString("iv"));
        byte[] data = Base64.getDecoder().decode(wrapper.getString("data"));

        SecretKey key = deriveKey(backupPassword, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword(); // بهداشت کلید
        }
    }
}
