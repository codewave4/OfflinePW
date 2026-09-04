private static class CryptoManager {
        private static final String KEY_ALIAS = "OfflinePW_Master_Key_v3";
        private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

        public CryptoManager(Context context) {
            getOrCreateKey();
        }

        private synchronized javax.crypto.SecretKey getOrCreateKey() {
            try {
                java.security.KeyStore keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE);
                keyStore.load(null);
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    java.security.KeyStore.SecretKeyEntry entry = (java.security.KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
                    return entry.getSecretKey();
                }

                try {
                    // تلاش اول با استفاده از تراشه سخت‌افزاری اختصاصی StrongBox
                    javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance(
                            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                    android.security.keystore.KeyGenParameterSpec spec = new android.security.keystore.KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(false)
                            .setIsStrongBoxBacked(true)
                            .build();
                    keyGen.init(spec);
                    return keyGen.generateKey();
                } catch (Exception e) {
                    // در صورت عدم وجود StrongBox، فال‌بک به پردازنده امن TEE
                    javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance(
                            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                    android.security.keystore.KeyGenParameterSpec spec = new android.security.keystore.KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(false)
                            .build();
                    keyGen.init(spec);
                    return keyGen.generateKey();
                }
            } catch (Exception e) {
                return null;
            }
        }

        public String encrypt(String plainText) {
            if (plainText == null || plainText.isEmpty()) return "";
            try {
                javax.crypto.SecretKey key = getOrCreateKey();
                if (key == null) return "";
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] iv = cipher.getIV();
                byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                return Base64.encodeToString(combined, Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        public String decrypt(String base64) {
            if (base64 == null || base64.isEmpty()) return "";
            try {
                javax.crypto.SecretKey key = getOrCreateKey();
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
            } catch (Exception e) {
                return "";
            }
        }
    }
