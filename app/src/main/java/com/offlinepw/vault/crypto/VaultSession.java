package com.offlinepw.vault.crypto;

import javax.crypto.SecretKey;

public class VaultSession {
    private static SecretKey dek;

    public static void setDek(SecretKey key) {
        dek = key;
    }

    public static SecretKey getDek() {
        return dek;
    }

    public static boolean hasDek() {
        return dek != null;
    }

    public static void clear() {
        dek = null;
    }
}
