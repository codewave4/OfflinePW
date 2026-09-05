package com.offlinepw.vault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthActivity extends AppCompatActivity {
    private static final int PIN_LENGTH = 8;
    private static final String PREF_AUTH = "OfflinePW_Auth";
    private static final String PREF_SETTINGS = "OfflinePW_Prefs";
    private static final String KEY_PIN_HASH = "master_pin_hash";
    private static final String KEY_PIN_SALT = "master_pin_salt";
    private static final String KEY_IS_SETUP = "pin_is_setup";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";

    private static final int PBKDF2_ITERATIONS = 120000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int LOCKOUT_THRESHOLD = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L;

    private SharedPreferences authPrefs;
    private SharedPreferences settingsPrefs;
    private TextView tvAuthPrompt;
    private TextView tvAuthWarning;
    private TextView tvPinIndicator;
    private MaterialButton btnAuthLang;
    private final StringBuilder currentPin = new StringBuilder();
    private boolean isSettingUpPin = false;
    private String tempPinToConfirm = null;
    private boolean isPersian = false;

    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private Runnable lockoutTickRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_auth);

        authPrefs = getSharedPreferences(PREF_AUTH, MODE_PRIVATE);
        settingsPrefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        isPersian = settingsPrefs.getBoolean("is_persian", false);

        tvAuthPrompt = findViewById(R.id.tvAuthPrompt);
        tvAuthWarning = findViewById(R.id.tvAuthWarning);
        tvPinIndicator = findViewById(R.id.tvPinIndicator);
        btnAuthLang = findViewById(R.id.btnAuthLang);

        boolean isSetup = authPrefs.getBoolean(KEY_IS_SETUP, false);
        isSettingUpPin = !isSetup;

        if (btnAuthLang != null) {
            btnAuthLang.setOnClickListener(v -> {
                isPersian = !isPersian;
                settingsPrefs.edit().putBoolean("is_persian", isPersian).apply();
                if (!isLockedOut()) updateTexts();
            });
        }

        updateTexts();
        setupNumericKeypad();
        checkLockoutState();
    }

    private void updateTexts() {
        if (btnAuthLang != null) {
            btnAuthLang.setText(isPersian ? "FA" : "EN");
        }

        if (tvAuthWarning != null) {
            tvAuthWarning.setText(isPersian ?
                    "رمز عبور شما در برنامه ذخیره نخواهد شد؛ بنابراین اگر آن را فراموش کنید، بازیابی آن غیرممکن است." :
                    "Your master PIN is never stored; therefore, if forgotten, recovery is mathematically impossible.");
        }

        if (tvAuthPrompt != null) {
            if (isSettingUpPin) {
                if (tempPinToConfirm == null) {
                    tvAuthPrompt.setText(isPersian ? "یک رمز ۸ رقمی مستر تعیین کنید" : "Create an 8-digit Master PIN");
                } else {
                    tvAuthPrompt.setText(isPersian ? "تکرار رمز ۸ رقمی برای تأیید:" : "Confirm your 8-digit Master PIN:");
                }
            } else {
                tvAuthPrompt.setText(isPersian ? "رمز مستر ۸ رقمی را وارد کنید" : "Enter your 8-digit Master PIN");
            }
        }

        updateIndicator();
    }

    private void setupNumericKeypad() {
        int[] buttonIds = new int[]{
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    if (isLockedOut()) {
                        return;
                    }
                    if (currentPin.length() < PIN_LENGTH) {
                        currentPin.append(btn.getText().toString());
                        updateIndicator();
                        if (currentPin.length() == PIN_LENGTH) {
                            handlePinComplete();
                        }
                    }
                });
            }
        }

        Button btnBackspace = findViewById(R.id.btnBackspace);
        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (isLockedOut()) return;
                if (currentPin.length() > 0) {
                    currentPin.deleteCharAt(currentPin.length() - 1);
                    updateIndicator();
                }
            });
        }
    }

    private void updateIndicator() {
        if (tvPinIndicator == null || isLockedOut()) return;
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < currentPin.length(); i++) {
            dots.append("● ");
        }
        for (int i = currentPin.length(); i < PIN_LENGTH; i++) {
            dots.append("○ ");
        }
        tvPinIndicator.setText(dots.toString().trim());
    }

    private void handlePinComplete() {
        String enteredPin = currentPin.toString();

        if (isSettingUpPin) {
            if (tempPinToConfirm == null) {
                tempPinToConfirm = enteredPin;
                currentPin.setLength(0);
                updateIndicator();
                updateTexts();
            } else {
                if (tempPinToConfirm.equals(enteredPin)) {
                    byte[] salt = generateSalt();
                    String hash = hashPin(enteredPin, salt);
                    authPrefs.edit()
                            .putString(KEY_PIN_HASH, hash)
                            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putBoolean(KEY_IS_SETUP, true)
                            .putInt(KEY_FAILED_ATTEMPTS, 0)
                            .putLong(KEY_LOCKOUT_UNTIL, 0)
                            .apply();
                    Toast.makeText(this, isPersian ? "رمز مستر با موفقیت ثبت شد" : "Master PIN set successfully", Toast.LENGTH_SHORT).show();
                    proceedToMain();
                } else {
                    Toast.makeText(this, isPersian ? "رمزها مطابقت ندارند، دوباره امتحان کنید" : "PINs do not match, try again", Toast.LENGTH_SHORT).show();
                    tempPinToConfirm = null;
                    currentPin.setLength(0);
                    updateIndicator();
                    updateTexts();
                }
            }
        } else {
            String savedHash = authPrefs.getString(KEY_PIN_HASH, "");
            String savedSaltB64 = authPrefs.getString(KEY_PIN_SALT, "");

            boolean matched;
            if (!savedSaltB64.isEmpty()) {
                byte[] salt = Base64.decode(savedSaltB64, Base64.NO_WRAP);
                String enteredHash = hashPin(enteredPin, salt);
                matched = savedHash.equals(enteredHash);
            } else {
                matched = savedHash.equals(legacyHashPin(enteredPin));
                if (matched) {
                    byte[] newSalt = generateSalt();
                    String newHash = hashPin(enteredPin, newSalt);
                    authPrefs.edit()
                            .putString(KEY_PIN_HASH, newHash)
                            .putString(KEY_PIN_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                            .apply();
                }
            }

            if (matched) {
                authPrefs.edit()
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .putLong(KEY_LOCKOUT_UNTIL, 0)
                        .apply();
                proceedToMain();
            } else {
                registerFailedAttempt();
                currentPin.setLength(0);
                if (!isLockedOut()) updateIndicator();
            }
        }
    }

    private void registerFailedAttempt() {
        int attempts = authPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();

        if (attempts >= LOCKOUT_THRESHOLD) {
            long lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            authPrefs.edit().putLong(KEY_LOCKOUT_UNTIL, lockoutUntil).apply();
            checkLockoutState();
        } else {
            int remaining = LOCKOUT_THRESHOLD - attempts;
            Toast.makeText(this, isPersian ?
                    ("رمز اشتباه است. " + remaining + " تلاش دیگر باقی مانده.") :
                    ("Incorrect PIN. " + remaining + " attempts remaining."), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLockedOut() {
        long lockoutUntil = authPrefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        return System.currentTimeMillis() < lockoutUntil;
    }

    private void checkLockoutState() {
        if (lockoutTickRunnable != null) {
            lockoutHandler.removeCallbacks(lockoutTickRunnable);
        }
        if (!isLockedOut()) {
            authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply();
            updateTexts();
            return;
        }
        lockoutTickRunnable = new Runnable() {
            @Override
            public void run() {
                long msLeft = authPrefs.getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis();
                if (msLeft <= 0) {
                    authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply();
                    updateTexts();
                    return;
                }
                long totalSeconds = msLeft / 1000;
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                String countdown = String.format(Locale.US, "%02d:%02d", minutes, seconds);

                if (tvAuthPrompt != null) {
                    tvAuthPrompt.setText(isPersian ? "قفل موقت به دلیل تلاشهای ناموفق" : "Locked due to failed attempts");
                }
                if (tvPinIndicator != null) {
                    tvPinIndicator.setText(countdown);
                }
                lockoutHandler.postDelayed(this, 1000);
            }
        };
        lockoutHandler.post(lockoutTickRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTickRunnable != null) {
            lockoutHandler.removeCallbacks(lockoutTickRunnable);
        }
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return toHex(hash);
        } catch (Exception e) {
            return legacyHashPin(pin);
        }
    }

    private String legacyHashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((pin + "OfflinePW_Salt_2026").getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            return pin;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
