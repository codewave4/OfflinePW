package com.offlinepw.vault.crypto;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    public CryptoManager(Context context) {
    }

    public String encrypt(String plainText, String aad) {
        if (plainText == null || plainText.isEmpty()) return "";
        try {
            SecretKey key = VaultSession.getDek();
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public String decrypt(String base64, String aad) {
        if (base64 == null || base64.isEmpty()) return "";
        try {
            SecretKey key = VaultSession.getDek();
            if (key == null) return "";
            byte[] combined = Base64.decode(base64, Base64.NO_WRAP);
            if (combined.length < IV_LENGTH) return "";
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
