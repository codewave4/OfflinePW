package com.offlinepw.vault;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TotpGenerator {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * اعتبارسنجی و نرمال‌سازی یک کلید Base32.
     *
     * @return رشته‌ی پاک‌سازی‌شده (بدون فاصله/خط تیره، حروف بزرگ، بدون padding) یا null اگر نامعتبر باشد.
     */
    public static String normalizeSecret(String secret) {
        if (secret == null) return null;
        String cleaned = secret.trim().toUpperCase()
                .replace("=", "")
                .replace(" ", "")
                .replace("-", "");
        if (cleaned.isEmpty()) return "";
        for (char c : cleaned.toCharArray()) {
            if (BASE32_CHARS.indexOf(c) < 0) return null;
        }
        // RFC 4226: کلید باید حداقل 128 بیت (26 کاراکتر Base32) باشد
        if (cleaned.length() < 16) return null;
        return cleaned;
    }

    public static boolean isValidSecret(String secret) {
        return normalizeSecret(secret) != null;
    }

    public static String generateCode(String secretBase32) {
        if (secretBase32 == null || secretBase32.trim().isEmpty()) {
            return "------";
        }
        try {
            byte[] key = base32Decode(secretBase32);
            if (key.length == 0) return "------";

            long timeStep = System.currentTimeMillis() / 1000 / 30;
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "RAW"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % 1000000;
            return String.format("%06d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "------";
        }
    }

    private static byte[] base32Decode(String input) {
        String cleaned = normalizeSecret(input);
        if (cleaned == null) return new byte[0];

        byte[] result = new byte[cleaned.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : cleaned.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) {
                return new byte[0];
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return java.util.Arrays.copyOf(result, index);
    }
}
