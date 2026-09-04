package com.offlinepw.vault;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TotpGenerator {
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static String generateCode(String base32Secret) {
        try {
            byte[] key = base32Decode(base32Secret);
            if (key.length == 0) return "------";
            long timeCounter = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
            byte[] counterBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counterBytes[i] = (byte) (timeCounter & 0xff);
                timeCounter >>= 8;
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0xf;
            int binaryCode =
                    ((hash[offset] & 0x7f) << 24) |
                    ((hash[offset + 1] & 0xff) << 16) |
                    ((hash[offset + 2] & 0xff) << 8) |
                    (hash[offset + 3] & 0xff);
            int otp = binaryCode % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            return "------";
        }
    }

    public static int getSecondsRemaining() {
        long epochSeconds = System.currentTimeMillis() / 1000L;
        return TIME_STEP_SECONDS - (int) (epochSeconds % TIME_STEP_SECONDS);
    }

    private static byte[] base32Decode(String input) {
        if (input == null) return new byte[0];
        String cleaned = input.trim().toUpperCase().replace("=", "").replace(" ", "");
        int byteCount = cleaned.length() * 5 / 8;
        byte[] result = new byte[byteCount];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : cleaned.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
