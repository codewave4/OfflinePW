package com.offlinepw.vault.crypto;

import javax.crypto.SecretKey;

/**
 * نگهدارنده‌ی نشست رمزنگاری (DEK) در حافظه.
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
        // reference is dropped; wiping the underlying bytes in Java/native memory is not guaranteed
        dek = null;
        decoy = false;
    }
}
