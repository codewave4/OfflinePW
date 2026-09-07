package com.offlinepw.vault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 6;
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
    private TextView tvLockoutTimer;
    private TextInputLayout tilMasterPassword;
    private TextInputEditText etMasterPassword;
    private MaterialButton btnUnlock;
    private MaterialButton btnAuthLang;
    private boolean isSettingUpPin = false;
    private String tempPasswordToConfirm = null;
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
        tvLockoutTimer = findViewById(R.id.tvLockoutTimer);
        tilMasterPassword = findViewById(R.id.tilMasterPassword);
        etMasterPassword = findViewById(R.id.etMasterPassword);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnAuthLang = findViewById(R.id.btnAuthLang);

        MaterialButton btnAuthHelp = findViewById(R.id.btnAuthHelp);
        if (btnAuthHelp != null) {
            btnAuthHelp.setOnClickListener(v -> {
                Intent intent = new Intent(this, WelcomeActivity.class);
                intent.putExtra("force_show", true);
                startActivity(intent);
            });
        }

        boolean isSetup = authPrefs.getBoolean(KEY_IS_SETUP, false);
        isSettingUpPin = !isSetup;

        if (btnAuthLang != null) {
            btnAuthLang.setOnClickListener(v -> {
                isPersian = !isPersian;
                settingsPrefs.edit().putBoolean("is_persian", isPersian).apply();
                if (!isLockedOut()) updateTexts();
            });
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> attemptSubmit());
        }

        if (etMasterPassword != null) {
            etMasterPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    attemptSubmit();
                    return true;
                }
                return false;
            });
        }

        updateTexts();
        checkLockoutState();
    }

    private void updateTexts() {
        if (btnAuthLang != null) {
            btnAuthLang.setText(isPersian ? "FA" : "EN");
        }

        if (tvAuthWarning != null) {
            tvAuthWarning.setText(isPersian ?
                    "رمز عبور شما در برنامه ذخیره نخواهد شد؛ بنابراین اگر آن را فراموش کنید، بازیابی آن غیرممکن است." :
                    "Your master password is never stored; therefore, if forgotten, recovery is mathematically impossible.");
        }

        if (tvAuthPrompt != null) {
            if (isSettingUpPin) {
                if (tempPasswordToConfirm == null) {
                    tvAuthPrompt.setText(isPersian ? "یک رمز عبور مستر تعیین کنید (حداقل ۶ کاراکتر)" : "Create a Master Password (min 6 characters)");
                } else {
                    tvAuthPrompt.setText(isPersian ? "تکرار رمز عبور برای تأیید:" : "Confirm your Master Password:");
                }
            } else {
                tvAuthPrompt.setText(isPersian ? "رمز عبور مستر را وارد کنید" : "Enter your Master Password");
            }
        }

        if (tilMasterPassword != null) {
            tilMasterPassword.setHint(isPersian ? "رمز عبور مستر" : "Master Password");
        }
        if (btnUnlock != null) {
            btnUnlock.setText(isPersian ? "باز کردن" : "Unlock");
        }

        if (etMasterPassword != null) etMasterPassword.setText("");
        showUnlockedUi();
    }

    private void attemptSubmit() {
        if (isLockedOut() || etMasterPassword == null) return;
        String entered = etMasterPassword.getText() != null ? etMasterPassword.getText().toString() : "";

        if (isSettingUpPin) {
            if (entered.length() < MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, isPersian ?
                        ("رمز عبور باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد") :
                        ("Password must be at least " + MIN_PASSWORD_LENGTH + " characters"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (tempPasswordToConfirm == null) {
                tempPasswordToConfirm = entered;
                updateTexts();
            } else {
                if (tempPasswordToConfirm.equals(entered)) {
                    byte[] salt = generateSalt();
                    String hash = hashPin(entered, salt);
                    authPrefs.edit()
                            .putString(KEY_PIN_HASH, hash)
                            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putBoolean(KEY_IS_SETUP, true)
                            .putInt(KEY_FAILED_ATTEMPTS, 0)
                            .putLong(KEY_LOCKOUT_UNTIL, 0)
                            .apply();
                    Toast.makeText(this, isPersian ? "رمز مستر با موفقیت ثبت شد" : "Master Password set successfully", Toast.LENGTH_SHORT).show();
                    proceedToMain();
                } else {
                    Toast.makeText(this, isPersian ? "رمزها مطابقت ندارند، دوباره امتحان کنید" : "Passwords do not match, try again", Toast.LENGTH_SHORT).show();
                    tempPasswordToConfirm = null;
                    updateTexts();
                }
            }
        } else {
            String savedHash = authPrefs.getString(KEY_PIN_HASH, "");
            String savedSaltB64 = authPrefs.getString(KEY_PIN_SALT, "");

            boolean matched;
            if (!savedSaltB64.isEmpty()) {
                byte[] salt = Base64.decode(savedSaltB64, Base64.NO_WRAP);
                String enteredHash = hashPin(entered, salt);
                matched = savedHash.equals(enteredHash);
            } else {
                matched = savedHash.equals(legacyHashPin(entered));
                if (matched) {
                    byte[] newSalt = generateSalt();
                    String newHash = hashPin(entered, newSalt);
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
                if (etMasterPassword != null) etMasterPassword.setText("");
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
                    ("Incorrect password. " + remaining + " attempts remaining."), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLockedOut() {
        long lockoutUntil = authPrefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        return System.currentTimeMillis() < lockoutUntil;
    }

    private void showUnlockedUi() {
        if (isLockedOut()) return;
        if (tvLockoutTimer != null) tvLockoutTimer.setVisibility(android.view.View.GONE);
        if (tilMasterPassword != null) tilMasterPassword.setVisibility(android.view.View.VISIBLE);
        if (btnUnlock != null) btnUnlock.setVisibility(android.view.View.VISIBLE);
    }

    private void showLockedUi() {
        if (tilMasterPassword != null) tilMasterPassword.setVisibility(android.view.View.GONE);
        if (btnUnlock != null) btnUnlock.setVisibility(android.view.View.GONE);
        if (tvLockoutTimer != null) tvLockoutTimer.setVisibility(android.view.View.VISIBLE);
        if (tvAuthPrompt != null) {
            tvAuthPrompt.setText(isPersian ? "قفل موقت به دلیل تلاش های ناموفق" : "Locked due to failed attempts");
        }
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
        showLockedUi();
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
                if (tvLockoutTimer != null) {
                    tvLockoutTimer.setText(countdown);
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

    private String hashPin(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return toHex(hash);
        } catch (Exception e) {
            return legacyHashPin(password);
        }
    }

    private String legacyHashPin(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((password + "OfflinePW_Salt_2026").getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            return password;
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
