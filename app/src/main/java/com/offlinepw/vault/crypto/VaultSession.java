package com.offlinepw.vault.crypto;

import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * نگهدارنده‌ی نشست رمزنگاری (DEK) در حافظه.
 * شامل تلاش برای پاک‌سازی مواد کلیدی پس از خروج از نشست.
 */
public class VaultSession {
    private static volatile SecretKey dek;
    /**
     * پرچم ولت فریبنده — صرفاً مسیر فایل دیتابیس را عوض می‌کند.
     * هیچ‌گاه در prefs ذخیره نمی‌شود؛ عمرش به عمر نشست (clear) محدود است.
     */
    private static volatile boolean decoy = false;

    public static void setDecoy(boolean isDecoy) {
        decoy = isDecoy;
    }

    public static boolean isDecoy() {
        return decoy;
    }

    public static void setDek(SecretKey key) {
        clear(); // اگر کلید قبلی پاک‌نشده مانده بود، اول پاکش کن
        dek = key;
    }

    public static SecretKey getDek() {
        return dek;
    }

    public static boolean hasDek() {
        return dek != null;
    }

    public static void clear() {
        SecretKey current = dek;
        dek = null;
        decoy = false; // حالت فریبنده هم با قفل شدن نشست پاک می‌شود
        if (current != null) {
            try {
                byte[] encoded = current.getEncoded();
                if (encoded != null) Arrays.fill(encoded, (byte) 0);
                if (current instanceof SecretKeySpec) {
                    ((SecretKeySpec) current).destroy();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
