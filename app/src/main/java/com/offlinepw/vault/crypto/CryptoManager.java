package com.offlinepw.vault.crypto;

import android.content.Context;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * رمزنگاری فیلدها با AES-256-GCM + AAD و قالب نسخه‌دار:
 *   payload = [version(1 byte)] [iv(12)] [ciphertext + tag]
 * نسخه‌ی 0x01 = فرمت فعلی. بلاب‌های قدیمی (قبل از نسخه‌گذاری) بدون بایت نسخه
 * به‌صورت خودکار شناسایی و خوانده می‌شوند تا ارتقای برنامه داده‌های قبلی را نشکند.
 */
public class CryptoManager {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final byte FORMAT_VERSION = 0x01;

    public CryptoManager(Context context) {
    }

    public static class CryptoException extends Exception {
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public String encrypt(String plainText, String aad) {
        if (plainText == null || plainText.isEmpty()) return "";
        try {
            SecretKey key = VaultSession.getDek();
            if (key == null) throw new IllegalStateException("DEK is not set");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[1 + iv.length + cipherText.length];
            combined[0] = FORMAT_VERSION;
            System.arraycopy(iv, 0, combined, 1, iv.length);
            System.arraycopy(cipherText, 0, combined, 1 + iv.length, cipherText.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String base64, String aad) throws CryptoException {
        if (base64 == null || base64.isEmpty()) return "";
        try {
            SecretKey key = VaultSession.getDek();
            if (key == null) throw new IllegalStateException("DEK is not set");
            byte[] combined = Base64.decode(base64, Base64.NO_WRAP);

            // قالب فعلی: [0x01][iv][ct]
            if (looksVersioned(combined)) {
                try {
                    return decryptWithOffset(combined, 1, key, aad);
                } catch (CryptoException firstAttempt) {
                    // تلاش مجدد با قالب قدیمی: اگر تصادفاً بایت اول یک بلاب قدیمی
                    // برابر 0x01 باشد، باز هم داده از دست نمی‌رود.
                    if (combined.length >= IV_LENGTH + 1) {
                        try {
                            return decryptWithOffset(combined, 0, key, aad);
                        } catch (CryptoException secondAttempt) {
                            throw firstAttempt;
                        }
                    }
                    throw firstAttempt;
                }
            }
            // قالب قدیمی (legacy): [iv][ct]
            return decryptWithOffset(combined, 0, key, aad);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Decryption failed for aad=" + aad, e);
        }
    }

    private boolean looksVersioned(byte[] combined) {
        // [0x01][iv 12B] + حداقل ۱ بایت متن رمز
        return combined.length > IV_LENGTH + 1 && combined[0] == FORMAT_VERSION;
    }

    private String decryptWithOffset(byte[] combined, int offset, SecretKey key, String aad) throws CryptoException {
        try {
            if (combined.length - offset < IV_LENGTH + 1) {
                throw new CryptoException("Payload too short for aad=" + aad, null);
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, offset, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - offset - IV_LENGTH];
            System.arraycopy(combined, offset + IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Decryption failed for aad=" + aad, e);
        }
    }
}
